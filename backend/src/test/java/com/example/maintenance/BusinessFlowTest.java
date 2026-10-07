package com.example.maintenance;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.maintenance.api.Requests.*;
import com.example.maintenance.auth.Actor;
import com.example.maintenance.common.BusinessException;
import com.example.maintenance.config.EntityFillHandler;
import com.example.maintenance.entity.*;
import com.example.maintenance.mapper.*;
import com.example.maintenance.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=${TEST_DB_URL:jdbc:h2:mem:test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1}", "spring.datasource.username=${TEST_DB_USERNAME:sa}", "spring.datasource.password=${TEST_DB_PASSWORD:}", "spring.datasource.driver-class-name=${TEST_DB_DRIVER:org.h2.Driver}", "app.seed=false"})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class BusinessFlowTest {
    @Autowired AuthService auth;
    @Autowired CommerceService commerce;
    @Autowired AdministrationService admin;
    @Autowired StoreService storeService;
    @Autowired UserMapper users;
    @Autowired StoreMapper stores;
    @Autowired ProductMapper products;
    @Autowired InventoryMapper inventories;
    @Autowired CartItemMapper carts;
    @Autowired ServiceOrderMapper orders;
    @Autowired VoucherMapper vouchers;
    @Autowired AppointmentMapper appointments;
    @Autowired ServiceSlotMapper slots;
    @Autowired MaintenanceRecordMapper records;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoSpyBean BusinessViews views;
    Actor administrator, customer, otherCustomer, shop, otherShop;
    Product product;
    ServiceSlot slot;
    @BeforeEach void prepare() {
        for (String table : List.of("audit_log","review","maintenance_record","service_voucher","appointment","store_service_slot","service_order_item","service_order","cart_item","inventory","product","store","sys_user")) jdbc.update("DELETE FROM " + table);
        administrator=auth.actor(auth.createUser("admin_test","13900000001","Password1","ADMIN","ACTIVE"));
        customer=auth.actor(auth.createUser("user_test","13900000002","Password1","CUSTOMER","ACTIVE"));
        otherCustomer=auth.actor(auth.createUser("other_test","13900000003","Password1","CUSTOMER","ACTIVE"));
        shop=createShop("shop_test","13900000004");otherShop=createShop("shop2_test","13900000005");
        product=createProduct("TEST-1","测试机油",10);
        slot=new ServiceSlot();slot.setStoreId(shop.storeId());slot.setServiceDate(EntityFillHandler.now().toLocalDate());slot.setStartTime("00:00");slot.setEndTime("23:59");slot.setCapacity(2);slot.setReservedCount(0);slot.setStatus("OPEN");slots.insert(slot);
    }
    Actor createShop(String name,String phone) {
        User user=auth.createUser(name,phone,"Password1","STORE","ACTIVE");Store s=new Store();s.setUserId(user.getId());s.setName(name);s.setContactName("测试联系人");s.setContactPhone(phone);s.setAddress("测试地址");s.setBusinessHours("全天");s.setAuditStatus("APPROVED");stores.insert(s);return auth.actor(user);
    }
    ProductInput input(String sku,String name,int stock,BigDecimal price) {return new ProductInput(sku,name,"润滑油","测试描述","/assets/oil.svg",price,new BigDecimal("20.00"),"ON_SHELF",stock);}
    Product createProduct(String sku,String name,int stock) {return (Product)admin.saveProduct(administrator,null,input(sku,name,stock,new BigDecimal("100.00"))).get("product");}
    ServiceOrder order(Actor owner,Product item,int quantity) {CartItem c=commerce.addCart(owner,new CartAdd(item.getId(),quantity));return (ServiceOrder)commerce.createOrder(owner,new CreateOrder(List.of(c.getId()),shop.storeId())).get("order");}
    Voucher approve(ServiceOrder order) {admin.reviewOrder(administrator,order.getId(),new Decision("APPROVED","测试通过"));return vouchers.selectOne(new QueryWrapper<Voucher>().eq("order_id",order.getId()));}
    String token(Actor actor) {return (String)auth.login(new Login(actor.loginName(),"Password1")).get("token");}
    void error(String code,org.junit.jupiter.api.function.Executable operation) {assertEquals(code,assertThrows(BusinessException.class,operation).getCode());}

    @Test void fullOrderAppointmentArrivalAndMaintenanceFlow() throws Exception {
        ServiceOrder order=order(customer,product,2);assertEquals(new BigDecimal("240.00"),order.getTotalAmount());assertEquals(2,inventories.selectById(product.getId()).getReservedQuantity());
        Voucher voucher=approve(order);assertTrue(voucher.getCode().matches("[0-9]{10}"));assertEquals(8,inventories.selectById(product.getId()).getOnHandQuantity());assertEquals(0,inventories.selectById(product.getId()).getReservedQuantity());
        commerce.book(customer,new Booking(order.getId(),slot.getId()));commerce.arrive(customer,new Arrival(shop.storeId(),voucher.getCode()));assertNotNull(appointments.selectOne(new QueryWrapper<Appointment>().eq("order_id",order.getId())).getArrivedAt());
        assertNotNull(storeService.verify(shop,voucher.getCode()));MaintenanceRecord record=(MaintenanceRecord)storeService.start(shop,voucher.getId()).get("record");
        storeService.complete(shop,record.getId(),new Complete("更换机油及滤清器","检查无异常"));assertEquals("COMPLETED",orders.selectById(order.getId()).getStatus());assertEquals("COMPLETED",records.selectById(record.getId()).getStatus());assertEquals(1L,commerce.myRecords(customer,1,12).get("total"));
        commerce.review(customer,new ReviewInput(order.getId(),"STORE",shop.storeId(),5,"服务很好"));error("ALREADY_REVIEWED",()->commerce.review(customer,new ReviewInput(order.getId(),"STORE",shop.storeId(),5,"再次评价")));
    }
    @Test void duplicateCustomerAndPasswordProtection() throws Exception {
        mvc.perform(post("/api/v1/auth/register/customer").contentType("application/json").content(json.writeValueAsString(new RegisterCustomer("new_user","13800000001","Secret123")))).andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/register/customer").contentType("application/json").content(json.writeValueAsString(new RegisterCustomer("new_user2","13800000001","Secret123")))).andExpect(status().isConflict());
        User user=users.selectOne(new QueryWrapper<User>().eq("login_name","new_user"));assertTrue(user.getPasswordHash().startsWith("$2"));assertFalse(json.writeValueAsString(user).contains("passwordHash"));
    }
    @Test void storeRegistrationRequiresApproval() {
        Map<String,Object> result=auth.registerStore(new RegisterStore("pending_shop","13800000009","Password1","申请店","联系人","13800000009","地址","09:00-18:00"));
        error("ACCOUNT_PENDING",()->auth.login(new Login("pending_shop","Password1")));admin.reviewStore(administrator,(Long)result.get("id"),new Decision("APPROVED","通过"));assertNotNull(auth.login(new Login("pending_shop","Password1")).get("token"));
        error("ALREADY_PROCESSED",()->admin.reviewStore(administrator,(Long)result.get("id"),new Decision("APPROVED","重复")));
    }
    @Test void latestPricesAndHistoricalSnapshots() {
        CartItem cart=commerce.addCart(customer,new CartAdd(product.getId(),1));admin.saveProduct(administrator,product.getId(),input(product.getSku(),product.getName(),10,new BigDecimal("150.00")));
        ServiceOrder order=(ServiceOrder)commerce.createOrder(customer,new CreateOrder(List.of(cart.getId()),shop.storeId())).get("order");assertEquals(new BigDecimal("170.00"),order.getTotalAmount());
        admin.saveProduct(administrator,product.getId(),input(product.getSku(),product.getName(),10,new BigDecimal("200.00")));assertEquals(new BigDecimal("170.00"),orders.selectById(order.getId()).getTotalAmount());
    }
    @Test void failedMultiProductOrderRollsBackAllReservationsAndKeepsCart() {
        Product second=createProduct("TEST-2","测试滤芯",1);CartItem first=commerce.addCart(customer,new CartAdd(product.getId(),1));CartItem last=commerce.addCart(customer,new CartAdd(second.getId(),1));
        order(otherCustomer,second,1);error("STOCK_NOT_ENOUGH",()->commerce.createOrder(customer,new CreateOrder(List.of(first.getId(),last.getId()),shop.storeId())));
        assertEquals(0,inventories.selectById(product.getId()).getReservedQuantity());assertEquals(2,carts.selectCount(new QueryWrapper<CartItem>().eq("user_id",customer.id())));assertEquals(1,orders.selectCount(null));
    }
    @Test void rejectReleasesStockAndDuplicateReviewCannotChangeInventory() {
        ServiceOrder order=order(customer,product,3);admin.reviewOrder(administrator,order.getId(),new Decision("REJECTED","不适配"));assertEquals(0,inventories.selectById(product.getId()).getReservedQuantity());assertEquals(10,inventories.selectById(product.getId()).getOnHandQuantity());
        error("ORDER_ALREADY_REVIEWED",()->admin.reviewOrder(administrator,order.getId(),new Decision("APPROVED","重复")));assertEquals(0,vouchers.selectCount(null));
    }
    @Test void approvalFailureRollsBackInventoryVoucherAndOrder() {
        ServiceOrder order=order(customer,product,2);doThrow(new IllegalStateException("模拟审计写入失败")).when(views).audit(anyLong(),eq("ORDER_REVIEW"),anyString(),anyLong(),anyString());
        assertThrows(IllegalStateException.class,()->approve(order));assertEquals("PENDING_REVIEW",orders.selectById(order.getId()).getStatus());assertEquals(10,inventories.selectById(product.getId()).getOnHandQuantity());assertEquals(2,inventories.selectById(product.getId()).getReservedQuantity());assertEquals(0,vouchers.selectCount(null));
    }
    @Test void wrongStoreAndDuplicateVoucherAreBlocked() {
        ServiceOrder order=order(customer,product,1);Voucher voucher=approve(order);commerce.book(customer,new Booking(order.getId(),slot.getId()));error("VOUCHER_INVALID",()->storeService.verify(otherShop,voucher.getCode()));error("VOUCHER_INVALID",()->storeService.start(otherShop,voucher.getId()));
        storeService.start(shop,voucher.getId());error("VOUCHER_USED",()->storeService.start(shop,voucher.getId()));assertEquals(1,records.selectCount(null));
    }
    @Test void duplicateAndWrongStoreAppointmentsAreBlocked() {
        ServiceOrder order=order(customer,product,1);approve(order);ServiceSlot other=new ServiceSlot();other.setStoreId(otherShop.storeId());other.setServiceDate(slot.getServiceDate());other.setStartTime("00:00");other.setEndTime("23:59");other.setCapacity(1);other.setReservedCount(0);other.setStatus("OPEN");slots.insert(other);
        error("SLOT_INVALID",()->commerce.book(customer,new Booking(order.getId(),other.getId())));commerce.book(customer,new Booking(order.getId(),slot.getId()));error("ALREADY_BOOKED",()->commerce.book(customer,new Booking(order.getId(),slot.getId())));assertEquals(1,slots.selectById(slot.getId()).getReservedCount());
    }
    @Test void orderOwnershipAndRoleChecksAreEnforcedByHttp() throws Exception {
        ServiceOrder order=order(customer,product,1);mvc.perform(get("/api/v1/orders/"+order.getOrderNo()).header("Authorization","Bearer "+token(otherCustomer))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/admin/dashboard").header("Authorization","Bearer "+token(customer))).andExpect(status().isForbidden());mvc.perform(get("/api/v1/cart")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/cart").header("Authorization","Bearer invalid.jwt.token")).andExpect(status().isUnauthorized());
    }
    @Test void offShelfAndPastDatesCannotBeUsed() {
        admin.saveProduct(administrator,product.getId(),new ProductInput(product.getSku(),product.getName(),product.getCategory(),"","",product.getPrice(),product.getLaborFee(),"OFF_SHELF",10));error("PRODUCT_OFF_SHELF",()->commerce.addCart(customer,new CartAdd(product.getId(),1)));
        admin.saveProduct(administrator,product.getId(),input(product.getSku(),product.getName(),10,product.getPrice()));ServiceOrder order=order(customer,product,1);approve(order);slot.setServiceDate(slot.getServiceDate().minusDays(1));slots.updateById(slot);error("SLOT_CLOSED",()->commerce.book(customer,new Booking(order.getId(),slot.getId())));
    }
    @Test void voucherRequiresAppointmentAndMatchingDate() {
        ServiceOrder order=order(customer,product,1);Voucher voucher=approve(order);error("NOT_BOOKED",()->storeService.verify(shop,voucher.getCode()));
        slot.setServiceDate(slot.getServiceDate().plusDays(1));slots.updateById(slot);commerce.book(customer,new Booking(order.getId(),slot.getId()));error("WRONG_APPOINTMENT_DATE",()->storeService.start(shop,voucher.getId()));
    }
    @Test void stockCannotBeSetBelowReservedAndDeletedProductsKeepOrders() {
        ServiceOrder order=order(customer,product,3);error("STOCK_RESERVED",()->admin.saveProduct(administrator,product.getId(),input(product.getSku(),product.getName(),2,product.getPrice())));
        admin.deleteProduct(administrator,product.getId());assertNotNull(commerce.orderDetail(customer,order.getOrderNo()));assertNotNull(approve(order));
    }
    private long race(Callable<Boolean> first,Callable<Boolean> second) throws Exception {
        ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch ready=new CountDownLatch(2),start=new CountDownLatch(1);
        try {
            List<Future<Boolean>> futures=new ArrayList<>();for(Callable<Boolean> action:List.of(first,second))futures.add(pool.submit(()->{ready.countDown();start.await();try{return action.call();}catch(BusinessException e){return false;}}));
            assertTrue(ready.await(5,TimeUnit.SECONDS));start.countDown();long success=0;for(Future<Boolean> f:futures)if(f.get(10,TimeUnit.SECONDS))success++;return success;
        } finally {pool.shutdownNow();}
    }
    @Test void concurrentLastStockCanBeReservedOnlyOnce() throws Exception {
        admin.saveProduct(administrator,product.getId(),input(product.getSku(),product.getName(),1,product.getPrice()));CartItem a=commerce.addCart(customer,new CartAdd(product.getId(),1)),b=commerce.addCart(otherCustomer,new CartAdd(product.getId(),1));
        assertEquals(1,race(()->{commerce.createOrder(customer,new CreateOrder(List.of(a.getId()),shop.storeId()));return true;},()->{commerce.createOrder(otherCustomer,new CreateOrder(List.of(b.getId()),shop.storeId()));return true;}));assertEquals(1,inventories.selectById(product.getId()).getReservedQuantity());assertEquals(1,orders.selectCount(null));
    }
    @Test void concurrentLastAppointmentCanBeBookedOnlyOnce() throws Exception {
        ServiceOrder a=order(customer,product,1),b=order(otherCustomer,product,1);approve(a);approve(b);slot.setCapacity(1);slots.updateById(slot);
        assertEquals(1,race(()->{commerce.book(customer,new Booking(a.getId(),slot.getId()));return true;},()->{commerce.book(otherCustomer,new Booking(b.getId(),slot.getId()));return true;}));assertEquals(1,slots.selectById(slot.getId()).getReservedCount());assertEquals(1,appointments.selectCount(null));
    }
    @Test void malformedQuantityAndReviewBeforeCompletionAreRejected() throws Exception {
        mvc.perform(post("/api/v1/cart/items").header("Authorization","Bearer "+token(customer)).contentType("application/json").content("{\"productId\":"+product.getId()+",\"quantity\":0}")).andExpect(status().isBadRequest());
        ServiceOrder order=order(customer,product,1);error("INVALID_ORDER_STATUS",()->commerce.review(customer,new ReviewInput(order.getId(),"STORE",shop.storeId(),5,"未完成评价")));
    }
}
