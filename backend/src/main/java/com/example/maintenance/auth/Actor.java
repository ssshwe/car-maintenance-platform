package com.example.maintenance.auth;

import com.example.maintenance.common.BusinessException;
import jakarta.servlet.http.HttpServletRequest;

public record Actor(Long id, String loginName, String role, Long storeId) {
    public static Actor from(HttpServletRequest request) {
        Actor actor = (Actor) request.getAttribute("actor");
        if (actor == null) throw new BusinessException("UNAUTHORIZED", "请先登录", 401);
        return actor;
    }
    public void requireRole(String expected) {
        if (!expected.equals(role)) throw new BusinessException("FORBIDDEN", "当前账号没有此操作权限", 403);
    }
}
