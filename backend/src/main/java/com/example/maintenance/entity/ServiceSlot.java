package com.example.maintenance.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("store_service_slot")
public class ServiceSlot {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long storeId;
    private LocalDate serviceDate;
    private String startTime;
    private String endTime;
    private Integer capacity;
    private Integer reservedCount;
    private String status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

