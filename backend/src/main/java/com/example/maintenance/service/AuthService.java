package com.example.maintenance.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.maintenance.api.Requests.*;
import com.example.maintenance.auth.*;
import com.example.maintenance.common.BusinessException;
import com.example.maintenance.entity.*;
import com.example.maintenance.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserMapper users;
    private final StoreMapper stores;
    private final TokenService tokens;
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();
    public User createUser(String loginName, String phone, String password, String role, String status) {
        BusinessException.require(password.getBytes(StandardCharsets.UTF_8).length <= 72, "VALIDATION_ERROR", "密码不能超过72个UTF-8字节");
        User user = new User(); user.setLoginName(loginName.trim()); user.setPhone(phone.trim());
        user.setPasswordHash(passwords.encode(password)); user.setRole(role); user.setStatus(status); users.insert(user); return user;
    }
    @Transactional
    public Map<String,Object> registerCustomer(RegisterCustomer input) {
        User user = createUser(input.loginName(), input.phone(), input.password(), "CUSTOMER", "ACTIVE");
        return Map.of("id", user.getId(), "message", "注册成功，请登录");
    }
    @Transactional
    public Map<String,Object> registerStore(RegisterStore input) {
        User user = createUser(input.loginName(), input.phone(), input.password(), "STORE", "PENDING");
        Store store = new Store(); store.setUserId(user.getId()); store.setName(input.name().trim());
        store.setContactName(input.contactName().trim()); store.setContactPhone(input.contactPhone());
        store.setAddress(input.address().trim()); store.setBusinessHours(input.businessHours().trim()); store.setAuditStatus("PENDING"); stores.insert(store);
        return Map.of("id", store.getId(), "message", "加盟申请已提交，请等待平台审核");
    }
    public Actor actor(User user) {
        Store store = "STORE".equals(user.getRole()) ? stores.selectOne(new QueryWrapper<Store>().eq("user_id", user.getId())) : null;
        return new Actor(user.getId(), user.getLoginName(), user.getRole(), store == null ? null : store.getId());
    }
    public Map<String,Object> login(Login input) {
        User user = users.selectOne(new QueryWrapper<User>().eq("login_name", input.loginName().trim()).or().eq("phone", input.loginName().trim()));
        if (user == null || input.password().getBytes(StandardCharsets.UTF_8).length > 72 || !passwords.matches(input.password(), user.getPasswordHash()))
            throw new BusinessException("LOGIN_FAILED", "账号或密码不正确", 401);
        if (!"ACTIVE".equals(user.getStatus())) {
            Store store = stores.selectOne(new QueryWrapper<Store>().eq("user_id", user.getId()));
            boolean rejected = store != null && "REJECTED".equals(store.getAuditStatus());
            throw new BusinessException(rejected ? "ACCOUNT_REJECTED" : "ACCOUNT_PENDING", rejected ? "加盟申请未通过：" + store.getAuditComment() : "加盟申请正在审核，请等待平台审核通过", 403);
        }
        Actor actor = actor(user); return Map.of("token", tokens.issue(actor), "user", actor);
    }
}
