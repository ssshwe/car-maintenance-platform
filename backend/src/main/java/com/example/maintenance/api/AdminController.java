package com.example.maintenance.api;

import com.example.maintenance.auth.Actor;
import com.example.maintenance.common.ApiResult;
import com.example.maintenance.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {
    private final AdministrationService admin;
    private final CatalogService catalog;
    @GetMapping("/dashboard") public ApiResult<?> dashboard(HttpServletRequest r) { return ApiResult.ok(admin.dashboard(Actor.from(r))); }
    @GetMapping("/stores") public ApiResult<?> stores(HttpServletRequest r,@RequestParam(required=false) String status,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="12") int size) { return ApiResult.ok(admin.storeList(Actor.from(r),status,page,size)); }
    @GetMapping("/stores/pending") public ApiResult<?> pendingStores(HttpServletRequest r,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="12") int size) { return ApiResult.ok(admin.storeList(Actor.from(r),"PENDING",page,size)); }
    @PostMapping("/stores/{id}/review") public ApiResult<?> reviewStore(HttpServletRequest r,@PathVariable Long id,@Valid @RequestBody Requests.Decision b) { admin.reviewStore(Actor.from(r),id,b); return ApiResult.ok(null); }
    @GetMapping("/products") public ApiResult<?> products(@RequestParam(required=false) String keyword,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="12") int size) { return ApiResult.ok(catalog.products(keyword,null,page,size,true)); }
    @PostMapping("/products") public ApiResult<?> createProduct(HttpServletRequest r,@Valid @RequestBody Requests.ProductInput b) { return ApiResult.ok(admin.saveProduct(Actor.from(r),null,b)); }
    @PutMapping("/products/{id}") public ApiResult<?> updateProduct(HttpServletRequest r,@PathVariable Long id,@Valid @RequestBody Requests.ProductInput b) { return ApiResult.ok(admin.saveProduct(Actor.from(r),id,b)); }
    @DeleteMapping("/products/{id}") public ApiResult<?> deleteProduct(HttpServletRequest r,@PathVariable Long id) { admin.deleteProduct(Actor.from(r),id); return ApiResult.ok(null); }
    @GetMapping("/orders") public ApiResult<?> orders(HttpServletRequest r,@RequestParam(required=false) String status,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="12") int size) { return ApiResult.ok(admin.orders(Actor.from(r),status,page,size)); }
    @GetMapping("/orders/pending") public ApiResult<?> pendingOrders(HttpServletRequest r,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="12") int size) { return ApiResult.ok(admin.orders(Actor.from(r),"PENDING_REVIEW",page,size)); }
    @PostMapping("/orders/{id}/review") public ApiResult<?> reviewOrder(HttpServletRequest r,@PathVariable Long id,@Valid @RequestBody Requests.Decision b) { admin.reviewOrder(Actor.from(r),id,b); return ApiResult.ok(null); }
    @GetMapping("/audit-logs") public ApiResult<?> audits(HttpServletRequest r,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="12") int size) { return ApiResult.ok(admin.audits(Actor.from(r),page,size)); }
}
