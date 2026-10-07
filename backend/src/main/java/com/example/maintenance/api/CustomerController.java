package com.example.maintenance.api;

import com.example.maintenance.auth.Actor;
import com.example.maintenance.common.ApiResult;
import com.example.maintenance.service.CommerceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CustomerController {
    private final CommerceService commerce;
    @GetMapping("/cart") public ApiResult<?> cart(HttpServletRequest r) { return ApiResult.ok(commerce.cart(Actor.from(r))); }
    @PostMapping("/cart/items") public ApiResult<?> add(HttpServletRequest r,@Valid @RequestBody Requests.CartAdd b) { return ApiResult.ok(commerce.addCart(Actor.from(r),b)); }
    @PutMapping("/cart/items/{id}") public ApiResult<?> update(HttpServletRequest r,@PathVariable Long id,@Valid @RequestBody Requests.Quantity b) { commerce.updateCart(Actor.from(r),id,b.quantity()); return ApiResult.ok(null); }
    @DeleteMapping("/cart/items/{id}") public ApiResult<?> delete(HttpServletRequest r,@PathVariable Long id) { commerce.removeCart(Actor.from(r),id); return ApiResult.ok(null); }
    @PostMapping("/orders") public ApiResult<?> order(HttpServletRequest r,@Valid @RequestBody Requests.CreateOrder b) { return ApiResult.ok(commerce.createOrder(Actor.from(r),b)); }
    @GetMapping("/orders/mine") public ApiResult<?> orders(HttpServletRequest r,@RequestParam(required=false) String status,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="12") int size) { return ApiResult.ok(commerce.myOrders(Actor.from(r),status,page,size)); }
    @GetMapping("/orders/{orderNo}") public ApiResult<?> detail(HttpServletRequest r,@PathVariable String orderNo) { return ApiResult.ok(commerce.orderDetail(Actor.from(r),orderNo)); }
    @GetMapping("/slots") public ApiResult<?> slots(@RequestParam Long storeId,@RequestParam(required=false) LocalDate from,@RequestParam(required=false) LocalDate to) { return ApiResult.ok(commerce.slots(storeId,from,to)); }
    @PostMapping("/appointments") public ApiResult<?> book(HttpServletRequest r,@Valid @RequestBody Requests.Booking b) { return ApiResult.ok(commerce.book(Actor.from(r),b)); }
    @PostMapping("/check-ins") public ApiResult<?> arrive(HttpServletRequest r,@Valid @RequestBody Requests.Arrival b) { return ApiResult.ok(commerce.arrive(Actor.from(r),b)); }
    @GetMapping("/maintenance-records/mine") public ApiResult<?> records(HttpServletRequest r,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="12") int size) { return ApiResult.ok(commerce.myRecords(Actor.from(r),page,size)); }
    @PostMapping("/reviews") public ApiResult<?> review(HttpServletRequest r,@Valid @RequestBody Requests.ReviewInput b) { return ApiResult.ok(commerce.review(Actor.from(r),b)); }
}
