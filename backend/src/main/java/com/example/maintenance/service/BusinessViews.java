package com.example.maintenance.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.maintenance.entity.*;
import com.example.maintenance.mapper.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class BusinessViews {
    private final StoreMapper stores;
    private final UserMapper users;
    private final InventoryMapper inventories;
    private final OrderItemMapper items;
    private final VoucherMapper vouchers;
    private final AppointmentMapper appointments;
    private final ServiceSlotMapper slots;
    private final ServiceOrderMapper orders;
    private final AuditLogMapper audits;
    public <T,R> Map<String,Object> page(BaseMapper<T> mapper, QueryWrapper<T> query, int requestedPage, int requestedSize, Function<T,R> convert) {
        int page = Math.max(1, Math.min(requestedPage,100000)); int size = Math.max(1, Math.min(100,requestedSize));
        long total = mapper.selectCount(query);
        var records = mapper.selectList(query.last("LIMIT " + size + " OFFSET " + ((page-1)*size))).stream().map(convert).toList();
        return Map.of("records", records, "total", total, "page", page, "size", size);
    }
    public Map<String,Object> product(Product product) {
        Inventory inventory = inventories.selectById(product.getId());
        return Map.of("product", product, "stock", inventory.getOnHandQuantity(), "reserved", inventory.getReservedQuantity(), "available", inventory.getOnHandQuantity()-inventory.getReservedQuantity());
    }
    public Map<String,Object> appointment(Appointment appointment) {
        Map<String,Object> view = new LinkedHashMap<>(); view.put("appointment", appointment);
        view.put("slot", slots.selectById(appointment.getSlotId())); return view;
    }
    public Map<String,Object> order(ServiceOrder order, boolean includeCode) {
        Map<String,Object> view = new LinkedHashMap<>();
        view.put("order", order); view.put("items", items.selectList(new QueryWrapper<OrderItem>().eq("order_id", order.getId()).orderByAsc("id")));
        view.put("store", stores.selectById(order.getStoreId()));
        User owner = users.selectById(order.getUserId()); String phone = owner.getPhone();
        view.put("customerName", owner.getLoginName()); view.put("customerPhone", phone.substring(0,3) + "****" + phone.substring(7));
        if (includeCode) view.put("voucher", vouchers.selectOne(new QueryWrapper<Voucher>().eq("order_id", order.getId())));
        Appointment appointment = appointments.selectOne(new QueryWrapper<Appointment>().eq("order_id", order.getId()));
        view.put("booking", appointment == null ? null : appointment(appointment)); return view;
    }
    public Map<String,Object> record(MaintenanceRecord record) {
        Map<String,Object> view = new LinkedHashMap<>(); view.put("record", record);
        view.put("order", orders.selectById(record.getOrderId())); view.put("store", stores.selectById(record.getStoreId()));
        view.put("booking", appointment(appointments.selectById(record.getAppointmentId()))); return view;
    }
    public void audit(Long actorId, String action, String targetType, Long targetId, String detail) {
        AuditLog log = new AuditLog(); log.setActorId(actorId); log.setAction(action); log.setTargetType(targetType); log.setTargetId(targetId);
        log.setDetail(detail != null && detail.length() > 2000 ? detail.substring(0,2000) : detail); log.setTraceId(Optional.ofNullable(MDC.get("traceId")).orElse(UUID.randomUUID().toString())); audits.insert(log);
    }
}
