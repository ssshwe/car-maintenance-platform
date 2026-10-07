package com.example.maintenance.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.example.maintenance.api.Requests.*;
import com.example.maintenance.auth.Actor;
import com.example.maintenance.common.BusinessException;
import com.example.maintenance.config.EntityFillHandler;
import com.example.maintenance.entity.*;
import com.example.maintenance.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static com.example.maintenance.common.BusinessException.require;

@Service
@RequiredArgsConstructor
public class CommerceService {
    private final UserMapper users;
    private final ProductMapper products;
    private final InventoryMapper inventories;
    private final CartItemMapper cart;
    private final StoreMapper stores;
    private final ServiceOrderMapper orders;
    private final OrderItemMapper items;
    private final ServiceSlotMapper slots;
    private final AppointmentMapper appointments;
    private final VoucherMapper vouchers;
    private final MaintenanceRecordMapper records;
    private final ReviewMapper reviews;
    private final BusinessViews views;
    public List<Map<String,Object>> cart(Actor actor) {
        return cart.selectList(new QueryWrapper<CartItem>().eq("user_id",actor.id()).orderByDesc("id")).stream().map(c -> {
            Map<String,Object> view = new LinkedHashMap<>(views.product(products.selectById(c.getProductId()))); view.put("cartItem",c); return view;
        }).toList();
    }
    @Transactional
    public CartItem addCart(Actor actor, CartAdd input) {
        users.lockById(actor.id()); Product product = products.selectById(input.productId());
        require(product != null && "ON_SHELF".equals(product.getStatus()), "PRODUCT_OFF_SHELF","商品已下架或不存在");
        CartItem row = cart.selectOne(new QueryWrapper<CartItem>().eq("user_id",actor.id()).eq("product_id",input.productId()));
        int quantity = input.quantity() + (row == null ? 0 : row.getQuantity()); require(quantity <= 99,"VALIDATION_ERROR","同一商品最多购买99件");
        Inventory inventory = inventories.selectById(input.productId()); require(quantity <= inventory.getOnHandQuantity()-inventory.getReservedQuantity(),"STOCK_NOT_ENOUGH","可用库存不足");
        if (row == null) { row = new CartItem(); row.setUserId(actor.id()); row.setProductId(input.productId()); row.setQuantity(quantity); cart.insert(row); }
        else { row.setQuantity(quantity); cart.updateById(row); } return row;
    }
    @Transactional
    public void updateCart(Actor actor, Long id, Integer quantity) {
        users.lockById(actor.id()); CartItem row = cart.selectById(id);
        if (row == null || !row.getUserId().equals(actor.id())) throw new BusinessException("NOT_FOUND","购物车记录不存在",404);
        Product product = products.selectById(row.getProductId()); require("ON_SHELF".equals(product.getStatus()),"PRODUCT_OFF_SHELF","商品已下架");
        Inventory inventory = inventories.selectById(row.getProductId()); require(quantity <= inventory.getOnHandQuantity()-inventory.getReservedQuantity(),"STOCK_NOT_ENOUGH","可用库存不足");
        row.setQuantity(quantity); cart.updateById(row);
    }
    @Transactional
    public void removeCart(Actor actor, Long id) { users.lockById(actor.id()); cart.delete(new QueryWrapper<CartItem>().eq("id",id).eq("user_id",actor.id())); }
    @Transactional
    public Map<String,Object> createOrder(Actor actor, CreateOrder input) {
        // 锁定账号，防止同一个购物车被重复提交；商品按ID排序避免多商品并发死锁。
        users.lockById(actor.id());
        Store store = stores.selectById(input.storeId()); require(store != null && "APPROVED".equals(store.getAuditStatus()),"STORE_UNAVAILABLE","请选择审核通过的门店");
        Set<Long> ids = new HashSet<>(input.cartItemIds()); require(ids.size() == input.cartItemIds().size(),"VALIDATION_ERROR","购物车项不能重复");
        var cartRows = cart.selectList(new QueryWrapper<CartItem>().eq("user_id",actor.id()).in("id",ids).orderByAsc("product_id"));
        require(cartRows.size() == ids.size(),"CART_CHANGED","购物车已变化，请刷新后重新下单");
        BigDecimal productAmount = BigDecimal.ZERO, laborAmount = BigDecimal.ZERO; List<OrderItem> orderItems = new ArrayList<>();
        for (CartItem row : cartRows) {
            Product product = products.lockById(row.getProductId()); require("ON_SHELF".equals(product.getStatus()),"PRODUCT_OFF_SHELF","商品“" + product.getName() + "”已下架");
            int changed = inventories.update(null,new UpdateWrapper<Inventory>().eq("product_id",product.getId())
                .apply("on_hand_quantity - reserved_quantity >= {0}",row.getQuantity()).setSql("reserved_quantity = reserved_quantity + {0}",row.getQuantity()).set("updated_at",EntityFillHandler.now()));
            require(changed == 1,"STOCK_NOT_ENOUGH","商品“" + product.getName() + "”库存不足");
            BigDecimal quantity = BigDecimal.valueOf(row.getQuantity());
            productAmount = productAmount.add(product.getPrice().multiply(quantity)); laborAmount = laborAmount.add(product.getLaborFee().multiply(quantity));
            OrderItem item = new OrderItem(); item.setProductId(product.getId()); item.setSkuSnapshot(product.getSku()); item.setNameSnapshot(product.getName());
            item.setUnitPriceSnapshot(product.getPrice()); item.setUnitLaborFeeSnapshot(product.getLaborFee()); item.setQuantity(row.getQuantity()); item.setLineAmount(product.getPrice().add(product.getLaborFee()).multiply(quantity)); orderItems.add(item);
        }
        BigDecimal total = productAmount.add(laborAmount); require(total.compareTo(new BigDecimal("99999999.99")) <= 0,"AMOUNT_TOO_LARGE","订单总额超出单次限制，请拆分下单");
        ServiceOrder order = new ServiceOrder(); order.setOrderNo("MC" + UUID.randomUUID().toString().replace("-","").substring(0,26).toUpperCase(Locale.ROOT));
        order.setUserId(actor.id()); order.setStoreId(input.storeId()); order.setStatus("PENDING_REVIEW"); order.setProductAmount(productAmount); order.setLaborAmount(laborAmount); order.setTotalAmount(total); orders.insert(order);
        orderItems.forEach(item -> { item.setOrderId(order.getId()); items.insert(item); });
        cart.delete(new QueryWrapper<CartItem>().eq("user_id",actor.id()).in("id",ids)); return views.order(order,true);
    }
    public ServiceOrder ownedOrder(Actor actor, Long id, boolean lock) {
        ServiceOrder order = lock ? orders.lockById(id) : orders.selectById(id);
        if (order == null || !order.getUserId().equals(actor.id())) throw new BusinessException("NOT_FOUND","订单不存在",404); return order;
    }
    public Map<String,Object> myOrders(Actor actor, String status, int page, int size) {
        var query = new QueryWrapper<ServiceOrder>().eq("user_id",actor.id()).eq(status != null && !status.isBlank(),"status",status).orderByDesc("id");
        return views.page(orders,query,page,size,o -> views.order(o,true));
    }
    public Map<String,Object> orderDetail(Actor actor, String no) {
        ServiceOrder order = orders.selectOne(new QueryWrapper<ServiceOrder>().eq("order_no",no).eq("user_id",actor.id()));
        if (order == null) throw new BusinessException("NOT_FOUND","订单不存在",404); return views.order(order,true);
    }
    public List<ServiceSlot> slots(Long storeId, LocalDate from, LocalDate to) {
        LocalDate today = EntityFillHandler.now().toLocalDate(); LocalDate start = from == null || from.isBefore(today) ? today : from;
        LocalDate end = to == null ? start.plusDays(14) : to; require(!end.isBefore(start) && !end.isAfter(start.plusDays(31)),"VALIDATION_ERROR","一次最多查询31天的时段");
        return slots.selectList(new QueryWrapper<ServiceSlot>().eq("store_id",storeId).eq("status","OPEN").between("service_date",start,end).orderByAsc("service_date","start_time"));
    }
    @Transactional
    public Map<String,Object> book(Actor actor, Booking input) {
        ServiceOrder order = ownedOrder(actor,input.orderId(),true); require("READY".equals(order.getStatus()),"INVALID_ORDER_STATUS","订单审核通过后才可预约");
        require(appointments.selectCount(new QueryWrapper<Appointment>().eq("order_id",order.getId())) == 0,"ALREADY_BOOKED","该订单已预约");
        ServiceSlot slot = slots.selectById(input.slotId());
        require(slot != null && slot.getStoreId().equals(order.getStoreId()),"SLOT_INVALID","预约时段与订单门店不匹配");
        var now = EntityFillHandler.now(); require(!slot.getServiceDate().isBefore(now.toLocalDate()) && !slot.getServiceDate().isAfter(now.toLocalDate().plusDays(31)),"SLOT_CLOSED","预约日期不在开放范围内");
        require(!slot.getServiceDate().equals(now.toLocalDate()) || java.time.LocalTime.parse(slot.getEndTime()).isAfter(now.toLocalTime()),"SLOT_CLOSED","该时段已结束");
        require("OPEN".equals(slot.getStatus()),"SLOT_CLOSED","该时段已关闭");
        int changed = slots.update(null,new UpdateWrapper<ServiceSlot>().eq("id",slot.getId()).eq("status","OPEN").apply("reserved_count < capacity").setSql("reserved_count = reserved_count + 1").set("updated_at",now));
        require(changed == 1,"SLOT_FULL","该时段已约满，请选择其他时段");
        Appointment appointment = new Appointment(); appointment.setOrderId(order.getId()); appointment.setUserId(actor.id()); appointment.setStoreId(order.getStoreId()); appointment.setSlotId(slot.getId()); appointment.setStatus("BOOKED"); appointments.insert(appointment);
        return views.appointment(appointment);
    }
    @Transactional
    public Map<String,Object> arrive(Actor actor, Arrival input) {
        Voucher voucher = vouchers.selectOne(new QueryWrapper<Voucher>().eq("code",input.code()).eq("store_id",input.storeId()));
        if (voucher == null) throw new BusinessException("VOUCHER_INVALID","凭据无效或不属于本门店",404);
        ServiceOrder order = ownedOrder(actor,voucher.getOrderId(),true);
        require("UNUSED".equals(voucher.getStatus()) && "READY".equals(order.getStatus()),"VOUCHER_USED","该凭据已使用或订单不可登记");
        Appointment appointment = appointments.selectOne(new QueryWrapper<Appointment>().eq("order_id",order.getId()));
        require(appointment != null,"NOT_BOOKED","请先预约到店日期");
        require(slots.selectById(appointment.getSlotId()).getServiceDate().equals(EntityFillHandler.now().toLocalDate()),"WRONG_APPOINTMENT_DATE","请在预约当天到店登记");
        if (appointment.getArrivedAt() == null) { appointment.setArrivedAt(EntityFillHandler.now()); appointments.updateById(appointment); views.audit(actor.id(),"CUSTOMER_ARRIVAL","ORDER",order.getId(),"车主扫码到店登记"); }
        return views.order(order,true);
    }
    public Map<String,Object> myRecords(Actor actor, int page, int size) { return views.page(records,new QueryWrapper<MaintenanceRecord>().eq("user_id",actor.id()).orderByDesc("id"),page,size,views::record); }
    @Transactional
    public Review review(Actor actor, ReviewInput input) {
        ServiceOrder order = ownedOrder(actor,input.orderId(),true); require("COMPLETED".equals(order.getStatus()),"INVALID_ORDER_STATUS","完成保养后才可评价");
        if ("STORE".equals(input.targetType())) require(order.getStoreId().equals(input.targetId()),"INVALID_TARGET","评价门店与订单不匹配");
        else require(items.selectCount(new QueryWrapper<OrderItem>().eq("order_id",order.getId()).eq("product_id",input.targetId())) > 0,"INVALID_TARGET","此订单没有该商品");
        require(reviews.selectCount(new QueryWrapper<Review>().eq("order_id",order.getId()).eq("target_type",input.targetType()).eq("target_id",input.targetId())) == 0,"ALREADY_REVIEWED","该项目已经评价过");
        Review review = new Review(); review.setOrderId(order.getId()); review.setUserId(actor.id()); review.setTargetType(input.targetType()); review.setTargetId(input.targetId()); review.setRating(input.rating()); review.setContent(input.content().trim()); reviews.insert(review); return review;
    }
}
