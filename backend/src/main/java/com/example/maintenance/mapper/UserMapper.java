package com.example.maintenance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.maintenance.entity.User;

public interface UserMapper extends BaseMapper<User> {
    @org.apache.ibatis.annotations.Select("SELECT * FROM sys_user WHERE id = #{id} FOR UPDATE")
    User lockById(Long id);
}

