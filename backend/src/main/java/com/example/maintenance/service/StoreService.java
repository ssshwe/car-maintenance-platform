package com.example.maintenance.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.maintenance.api.Requests.*;
import com.example.maintenance.auth.Actor;
import com.example.maintenance.common.BusinessException;
import com.example.maintenance.config.EntityFillHandler;
import com.example.maintenance.entity.*;
import com.example.maintenance.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import static com.example.maintenance.common.BusinessException.require;

@Service
@RequiredArgsConstructor
public class StoreService {
    private final StoreMapper stores;
    private final VoucherMapper vouchers;
    private final ServiceOrderMapper orders;
    private final AppointmentMapper appointments;
    private final ServiceSlotMapper slots;
    private final MaintenanceRecordMapper records;
    private final BusinessViews views;
    public Store profile(Actor actor) { actor.requireRole("STORE"); return stores.selectById(actor.storeId()); }
    public Map<String,Object> appointments(Actor actor,String date,int page,int size) {
        actor.requireRole("STORE"); var query = new QueryWrapper<Appointment>().eq("store_id",actor.storeId());
        if (date != null && !date.isBlank()) {
            var ids = slots.selectList(new QueryWrapper<ServiceSlot>().eq("store_id",actor.storeId()).eq("service_date",LocalDate.parse(date))).stream().map(ServiceSlot::getId).toList();
            if (ids.isEmpty()) return Map.of("records",List.of(),"total",0,"page",page,"size",size); query.in("slot_id",ids);
        }
        query.orderByDesc("id"); return views.page(appointments,query,page,size,a -> {
            Map<String,Object> view = new LinkedHashMap<>(views.appointment(a)); view.put("orderView",views.order(orders.selectById(a.getOrderId()),false)); return view;
        });
    }
    private Appointment validateVoucher(Actor actor,Voucher voucher,ServiceOrder order) {
        actor.requireRole("STORE");
        if (voucher == null || !voucher.getStoreId().equals(actor.storeId())) throw new BusinessException("VOUCHER_INVALID","凭据无效或不属于本门店",404);
        require("UNUSED".equals(voucher.getStatus()),"VOUCHER_USED","凭据已经核销");
        require(order != null && "READY".equals(order.getStatus()),"INVALID_ORDER_STATUS","订单当前不可开始服务");
        Appointment appointment = appointments.selectOne(new QueryWrapper<Appointment>().eq("order_id",order.getId()));
        require(appointment != null && "BOOKED".equals(appointment.getStatus()),"NOT_BOOKED","车主尚未预约或预约状态不可用");
        require(slots.selectById(appointment.getSlotId()).getServiceDate().equals(EntityFillHandler.now().toLocalDate()),"WRONG_APPOINTMENT_DATE","只能在预约当天核销凭据"); return appointment;
    }
    public Map<String,Object> verify(Actor actor,String code) {
        actor.requireRole("STORE"); Voucher voucher = vouchers.selectOne(new QueryWrapper<Voucher>().eq("code",code).eq("store_id",actor.storeId()));
        if (voucher == null) throw new BusinessException("VOUCHER_INVALID","凭据无效或不属于本门店",404);
        ServiceOrder order = orders.selectById(voucher.getOrderId()); validateVoucher(actor,voucher,order);
        return Map.of("voucherId",voucher.getId(),"orderView",views.order(order,false));
    }
    @Transactional
    public Map<String,Object> start(Actor actor,Long voucherId) {
        actor.requireRole("STORE"); Voucher candidate = vouchers.selectById(voucherId);
        if (candidate == null || !candidate.getStoreId().equals(actor.storeId())) throw new BusinessException("VOUCHER_INVALID","凭据无效或不属于本门店",404);
        ServiceOrder order = orders.lockById(candidate.getOrderId()); Voucher voucher = vouchers.lockById(voucherId); Appointment appointment = validateVoucher(actor,voucher,order);
        var now = EntityFillHandler.now(); voucher.setStatus("USED"); voucher.setUsedAt(now); vouchers.updateById(voucher);
        appointment.setStatus("CHECKED_IN"); if (appointment.getArrivedAt() == null) appointment.setArrivedAt(now); appointments.updateById(appointment);
        order.setStatus("IN_SERVICE"); orders.updateById(order);
        MaintenanceRecord record = new MaintenanceRecord(); record.setOrderId(order.getId()); record.setAppointmentId(appointment.getId()); record.setUserId(order.getUserId());
        record.setStoreId(actor.storeId()); record.setOperatorId(actor.id()); record.setStatus("IN_PROGRESS"); record.setStartedAt(now); records.insert(record);
        views.audit(actor.id(),"MAINTENANCE_START","ORDER",order.getId(),"凭据核销并开始服务"); return views.record(record);
    }
    @Transactional
    public void complete(Actor actor,Long recordId,Complete input) {
        actor.requireRole("STORE"); MaintenanceRecord candidate = records.selectById(recordId);
        if (candidate == null || !candidate.getStoreId().equals(actor.storeId())) throw new BusinessException("NOT_FOUND","保养记录不存在",404);
        ServiceOrder order = orders.lockById(candidate.getOrderId()); MaintenanceRecord record = records.lockById(recordId);
        require("IN_PROGRESS".equals(record.getStatus()) && "IN_SERVICE".equals(order.getStatus()),"ALREADY_PROCESSED","该服务已经完成");
        record.setServiceItems(input.serviceItems().trim()); record.setNote(input.note()); record.setCompletedAt(EntityFillHandler.now()); record.setStatus("COMPLETED"); record.setOperatorId(actor.id()); records.updateById(record);
        Appointment appointment = appointments.selectById(record.getAppointmentId()); appointment.setStatus("COMPLETED"); appointments.updateById(appointment);
        order.setStatus("COMPLETED"); orders.updateById(order); views.audit(actor.id(),"MAINTENANCE_COMPLETE","ORDER",order.getId(),"完成服务：" + input.serviceItems().trim());
    }
    public Map<String,Object> records(Actor actor,String status,int page,int size) {
        actor.requireRole("STORE"); return views.page(records,new QueryWrapper<MaintenanceRecord>().eq("store_id",actor.storeId()).eq(status != null && !status.isBlank(),"status",status).orderByDesc("id"),page,size,views::record);
    }
    public List<ServiceSlot> slots(Actor actor) { actor.requireRole("STORE"); return slots.selectList(new QueryWrapper<ServiceSlot>().eq("store_id",actor.storeId()).ge("service_date",EntityFillHandler.now().toLocalDate()).orderByAsc("service_date","start_time")); }
    @Transactional
    public ServiceSlot saveSlot(Actor actor,Long id,SlotInput input) {
        actor.requireRole("STORE"); require(!input.serviceDate().isBefore(EntityFillHandler.now().toLocalDate()) && !input.serviceDate().isAfter(EntityFillHandler.now().toLocalDate().plusDays(31)),"VALIDATION_ERROR","时段需在未来31天内");
        require(LocalTime.parse(input.endTime()).isAfter(LocalTime.parse(input.startTime())),"VALIDATION_ERROR","结束时间必须晚于开始时间");
        // 容量修改采用条件UPDATE，与预约占用共享数据库行锁。
        ServiceSlot slot = id == null ? new ServiceSlot() : slots.selectById(id);
        require(slot != null && (id == null || slot.getStoreId().equals(actor.storeId())),"NOT_FOUND","时段不存在");
        if (id != null) {
            require(slot.getServiceDate().equals(input.serviceDate()) && slot.getStartTime().equals(input.startTime()) && slot.getEndTime().equals(input.endTime()),"VALIDATION_ERROR","已建时段不能移动日期或时间，请另建时段");
            int changed = slots.update(null,new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<ServiceSlot>().eq("id",id).eq("store_id",actor.storeId()).le("reserved_count",input.capacity()).set("capacity",input.capacity()).set("status",input.status()).set("updated_at",EntityFillHandler.now()));
            require(changed == 1,"SLOT_RESERVED","容量不能小于已预约数量"); slot = slots.selectById(id);
        } else {
            slot.setStoreId(actor.storeId()); slot.setServiceDate(input.serviceDate()); slot.setStartTime(input.startTime()); slot.setEndTime(input.endTime()); slot.setCapacity(input.capacity()); slot.setReservedCount(0); slot.setStatus(input.status()); slots.insert(slot);
        }
        views.audit(actor.id(),"SLOT_UPDATE","SLOT",slot.getId(),"容量=" + input.capacity() + ",状态=" + input.status()); return slot;
    }
}
