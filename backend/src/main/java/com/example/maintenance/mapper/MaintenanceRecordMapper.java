package com.example.maintenance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.maintenance.entity.MaintenanceRecord;

public interface MaintenanceRecordMapper extends BaseMapper<MaintenanceRecord> {
    @org.apache.ibatis.annotations.Select("SELECT * FROM maintenance_record WHERE id = #{id} FOR UPDATE")
    MaintenanceRecord lockById(Long id);
}

