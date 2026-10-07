package com.example.maintenance.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Component
public class EntityFillHandler implements MetaObjectHandler {
    public static LocalDateTime now() { return LocalDateTime.now(ZoneId.of("Asia/Shanghai")); }
    @Override public void insertFill(MetaObject metaObject) {
        strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now());
        strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now());
    }
    @Override public void updateFill(MetaObject metaObject) { setFieldValByName("updatedAt", now(), metaObject); }
}
