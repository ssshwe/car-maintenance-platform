package com.example.maintenance.api;

import com.example.maintenance.auth.Actor;
import com.example.maintenance.common.ApiResult;
import com.example.maintenance.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService auth;
    @PostMapping("/register/customer") public ApiResult<?> customer(@Valid @RequestBody Requests.RegisterCustomer body) { return ApiResult.ok(auth.registerCustomer(body)); }
    @PostMapping("/register/store") public ApiResult<?> store(@Valid @RequestBody Requests.RegisterStore body) { return ApiResult.ok(auth.registerStore(body)); }
    @PostMapping("/login") public ApiResult<?> login(@Valid @RequestBody Requests.Login body) { return ApiResult.ok(auth.login(body)); }
    @GetMapping("/me") public ApiResult<?> me(HttpServletRequest request) { return ApiResult.ok(Actor.from(request)); }
}
