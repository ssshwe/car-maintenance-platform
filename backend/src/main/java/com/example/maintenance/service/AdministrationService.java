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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.*;
import static com.example.maintenance.common.BusinessException.require;

@Service
@RequiredArgsConstructor
public class AdministrationService {
    private final UserMapper users;
    private final StoreMapper stores;
    private final ProductMapper products;
    private final InventoryMapper inventories;
    private final ServiceOrderMapper orders;
    private final OrderItemMapper items;
    private final VoucherMapper vouchers;
    private final ServiceSlotMapper slots;
    private final MaintenanceRecordMapper records;
    private final AuditLogMapper audits;
    private final BusinessViews views;
    private final SecureRandom random = new SecureRandom();
    public Map<String,Object> dashboard(Actor actor) {
        actor.requireRole("ADMIN");
        return Map.of("customers",users.selectCount(new QueryWrapper<User>().eq("role","CUSTOMER")),
            "stores",stores.selectCount(new QueryWrapper<Store>().eq("audit_status","APPROVED")),
            "pendingStores",stores.selectCount(new QueryWrapper<Store>().eq("audit_status","PENDING")),
            "pendingOrders",orders.selectCount(new QueryWrapper<ServiceOrder>().eq("status","PENDING_REVIEW")),
            "orders",orders.selectCount(null),"completed",records.selectCount(new QueryWrapper<MaintenanceRecord>().eq("status","COMPLETED")));
    }
    public Map<String,Object> storeList(Actor actor,String status,int page,int size) {
        actor.requireRole("ADMIN"); return views.page(stores,new QueryWrapper<Store>().eq(status != null && !status.isBlank(),"audit_status",status).orderByDesc("id"),page,size,s -> s);
    }
    @Transactional
    public void reviewStore(Actor actor,Long id,Decision input) {
        actor.requireRole("ADMIN"); Store store = stores.selectById(id);
        if (store == null) throw new BusinessException("NOT_FOUND","门店不存在",404);
        users.lockById(store.getUserId()); store = stores.selectById(id);
        require("PENDING".equals(store.getAuditStatus()),"ALREADY_PROCESSED","该申请已经审核");
        require(!"REJECTED".equals(input.decision()) || input.comment() != null && !input.comment().isBlank(),"VALIDATION_ERROR","拒绝时请填写审核意见");
        store.setAuditStatus(input.decision()); store.setAuditUserId(actor.id()); store.setAuditAt(EntityFillHandler.now()); store.setAuditComment(input.comment()); stores.updateById(store);
        User user = users.selectById(store.getUserId()); user.setStatus("APPROVED".equals(input.decision()) ? "ACTIVE" : "DISABLED"); users.updateById(user);
        if ("APPROVED".equals(input.decision())) ensureDefaultSlots(store.getId());
        views.audit(actor.id(),"STORE_REVIEW","STORE",id,input.decision() + " " + Objects.toString(input.comment(),""));
    }
    public void ensureDefaultSlots(Long storeId) {
        LocalDate today = EntityFillHandler.now().toLocalDate();
        for (int i=0;i<14;i++) for (String start : List.of("09:00","14:00")) {
            LocalDate date = today.plusDays(i);
            if (slots.selectCount(new QueryWrapper<ServiceSlot>().eq("store_id",storeId).eq("service_date",date).eq("start_time",start)) > 0) continue;
            ServiceSlot slot = new ServiceSlot(); slot.setStoreId(storeId); slot.setServiceDate(date); slot.setStartTime(start); slot.setEndTime(start.equals("09:00") ? "12:00" : "18:00");
            slot.setCapacity(6); slot.setReservedCount(0); slot.setStatus("OPEN"); slots.insert(slot);
        }
    }
    @Transactional
    public Map<String,Object> saveProduct(Actor actor,Long id,ProductInput input) {
        actor.requireRole("ADMIN"); Product product = id == null ? new Product() : products.lockById(id);
        require(product != null && !"DELETED".equals(product.getStatus()),"NOT_FOUND","商品不存在");
        if (input.imageUrl() != null && !input.imageUrl().isBlank()) require(input.imageUrl().startsWith("/assets/") || input.imageUrl().matches("https?://[^\\s]+"),"VALIDATION_ERROR","图片地址必须为http(s)链接或/assets/路径");
        product.setSku(input.sku().trim()); product.setName(input.name().trim()); product.setCategory(input.category().trim()); product.setDescription(input.description()); product.setImageUrl(input.imageUrl());
        product.setPrice(input.price()); product.setLaborFee(input.laborFee()); product.setStatus(input.status());
        if (id == null) {
            product.setCreatedBy(actor.id()); products.insert(product); Inventory inventory = new Inventory(); inventory.setProductId(product.getId()); inventory.setOnHandQuantity(input.stock()); inventory.setReservedQuantity(0); inventories.insert(inventory);
        } else {
            int changed = inventories.update(null,new UpdateWrapper<Inventory>().eq("product_id",id).le("reserved_quantity",input.stock()).set("on_hand_quantity",input.stock()).set("updated_at",EntityFillHandler.now()));
            require(changed == 1,"STOCK_RESERVED","库存不能小于已经被订单预占的数量"); products.updateById(product);
        }
        views.audit(actor.id(),id == null ? "PRODUCT_CREATE" : "PRODUCT_UPDATE","PRODUCT",product.getId(),"商品=" + product.getName() + ",库存=" + input.stock() + ",售价=" + input.price() + ",工时费=" + input.laborFee()); return views.product(product);
    }
    @Transactional
    public void deleteProduct(Actor actor,Long id) {
        actor.requireRole("ADMIN"); Product product = products.lockById(id); require(product != null,"NOT_FOUND","商品不存在");
        product.setStatus("DELETED"); products.updateById(product); views.audit(actor.id(),"PRODUCT_DELETE","PRODUCT",id,"逻辑删除，保留订单快照与库存记录");
    }
    public Map<String,Object> orders(Actor actor,String status,int page,int size) {
        actor.requireRole("ADMIN"); return views.page(orders,new QueryWrapper<ServiceOrder>().eq(status != null && !status.isBlank(),"status",status).orderByDesc("id"),page,size,o -> views.order(o,false));
    }
    @Transactional
    public void reviewOrder(Actor actor,Long id,Decision input) {
        actor.requireRole("ADMIN"); ServiceOrder order = orders.lockById(id);
        require(order != null,"NOT_FOUND","订单不存在"); require("PENDING_REVIEW".equals(order.getStatus()),"ORDER_ALREADY_REVIEWED","订单已经审核，请勿重复操作");
        boolean approve = "APPROVED".equals(input.decision());
        require(approve || input.comment() != null && !input.comment().isBlank(),"VALIDATION_ERROR","拒绝时请填写审核意见");
        for (OrderItem item : items.selectList(new QueryWrapper<OrderItem>().eq("order_id",id).orderByAsc("product_id"))) {
            UpdateWrapper<Inventory> update = new UpdateWrapper<Inventory>().eq("product_id",item.getProductId()).ge("reserved_quantity",item.getQuantity()).setSql("reserved_quantity = reserved_quantity - {0}",item.getQuantity()).set("updated_at",EntityFillHandler.now());
            if (approve) update.ge("on_hand_quantity",item.getQuantity()).setSql("on_hand_quantity = on_hand_quantity - {0}",item.getQuantity());
            require(inventories.update(null,update) == 1,"INVENTORY_CONFLICT","库存状态异常，审核已回滚");
        }
        if (approve) {
            Voucher voucher = new Voucher(); voucher.setOrderId(id); voucher.setStoreId(order.getStoreId()); voucher.setStatus("UNUSED"); boolean issued = false;
            for (int attempt=0;attempt<3;attempt++) {
                voucher.setId(null); voucher.setCode(String.format(Locale.ROOT,"%010d",random.nextLong(10_000_000_000L)));
                try { vouchers.insert(voucher); issued = true; break; } catch (DuplicateKeyException collision) { /* 唯一索引冲突时重新生成 */ }
            }
            require(issued,"VOUCHER_CONFLICT","凭据生成冲突，请重试");
        }
        order.setStatus(approve ? "READY" : "REJECTED"); order.setReviewUserId(actor.id()); order.setReviewAt(EntityFillHandler.now()); order.setReviewComment(input.comment()); orders.updateById(order);
        views.audit(actor.id(),"ORDER_REVIEW","ORDER",id,input.decision() + " " + Objects.toString(input.comment(),""));
    }
    public Map<String,Object> audits(Actor actor,int page,int size) { actor.requireRole("ADMIN"); return views.page(audits,new QueryWrapper<AuditLog>().orderByDesc("id"),page,size,a -> a); }
}
