package com.example.maintenance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.maintenance.entity.Voucher;

public interface VoucherMapper extends BaseMapper<Voucher> {
    @org.apache.ibatis.annotations.Select("SELECT * FROM service_voucher WHERE id = #{id} FOR UPDATE")
    Voucher lockById(Long id);
}

