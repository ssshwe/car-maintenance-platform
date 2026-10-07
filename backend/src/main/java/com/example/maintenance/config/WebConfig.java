package com.example.maintenance.config;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.maintenance.auth.*;
import com.example.maintenance.common.BusinessException;
import com.example.maintenance.entity.Store;
import com.example.maintenance.mapper.*;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.*;
import org.slf4j.MDC;
import java.util.UUID;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {
    private final TokenService tokens;
    private final UserMapper users;
    private final StoreMapper stores;
    private final RateLimiter rateLimiter;
    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                String path = request.getRequestURI();
                if (path.equals("/api/v1/auth/login") || path.startsWith("/api/v1/auth/register/")) {
                    rateLimiter.check("auth:" + request.getRemoteAddr(), 30); return true;
                }
                if (request.getMethod().equals("GET") && (path.equals("/api/v1/products") || path.equals("/api/v1/stores") || path.equals("/api/v1/reviews"))) return true;
                String header = request.getHeader("Authorization");
                if (header == null || !header.startsWith("Bearer ")) throw new BusinessException("UNAUTHORIZED", "请先登录", 401);
                var user = users.selectById(tokens.verify(header.substring(7)));
                if (user == null || !"ACTIVE".equals(user.getStatus())) throw new BusinessException("UNAUTHORIZED", "账号不可用，请重新登录", 401);
                Store store = "STORE".equals(user.getRole()) ? stores.selectOne(new QueryWrapper<Store>().eq("user_id", user.getId())) : null;
                Actor actor = new Actor(user.getId(), user.getLoginName(), user.getRole(), store == null ? null : store.getId());
                request.setAttribute("actor", actor);
                if (path.startsWith("/api/v1/admin/")) actor.requireRole("ADMIN");
                else if (path.startsWith("/api/v1/store/")) actor.requireRole("STORE");
                else if (!path.startsWith("/api/v1/auth/")) actor.requireRole("CUSTOMER");
                if (path.contains("check-ins")) rateLimiter.check("checkin:" + actor.id() + ":" + request.getRemoteAddr(), 20);
                return true;
            }
        }).addPathPatterns("/api/v1/**");
    }
}
