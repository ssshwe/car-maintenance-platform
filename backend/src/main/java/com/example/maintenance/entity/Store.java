package com.example.maintenance.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("store")
public class Store {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String name;
    private String contactName;
    private String contactPhone;
    private String address;
    private String businessHours;
    private String auditStatus;
    private Long auditUserId;
    private LocalDateTime auditAt;
    private String auditComment;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

