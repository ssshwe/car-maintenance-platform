package com.example.maintenance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.maintenance.entity.Product;

public interface ProductMapper extends BaseMapper<Product> {
    @org.apache.ibatis.annotations.Select("SELECT * FROM product WHERE id = #{id} FOR UPDATE")
    Product lockById(Long id);
}

