package com.example.maintenance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.maintenance.entity.ServiceOrder;

public interface ServiceOrderMapper extends BaseMapper<ServiceOrder> {
    @org.apache.ibatis.annotations.Select("SELECT * FROM service_order WHERE id = #{id} FOR UPDATE")
    ServiceOrder lockById(Long id);
}

