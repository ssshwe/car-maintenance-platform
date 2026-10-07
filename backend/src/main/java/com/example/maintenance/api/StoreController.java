package com.example.maintenance.api;

import com.example.maintenance.auth.Actor;
import com.example.maintenance.common.ApiResult;
import com.example.maintenance.service.StoreService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/store")
@RequiredArgsConstructor
public class StoreController {
    private final StoreService store;
    @GetMapping("/profile") public ApiResult<?> profile(HttpServletRequest r) { return ApiResult.ok(store.profile(Actor.from(r))); }
    @GetMapping("/appointments") public ApiResult<?> appointments(HttpServletRequest r,@RequestParam(required=false) String date,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="12") int size) { return ApiResult.ok(store.appointments(Actor.from(r),date,page,size)); }
    @PostMapping("/check-ins/verify") public ApiResult<?> verify(HttpServletRequest r,@Valid @RequestBody Requests.Code b) { return ApiResult.ok(store.verify(Actor.from(r),b.code())); }
    @PostMapping("/check-ins/{voucherId}/start") public ApiResult<?> start(HttpServletRequest r,@PathVariable Long voucherId) { return ApiResult.ok(store.start(Actor.from(r),voucherId)); }
    @GetMapping("/maintenance-records") public ApiResult<?> records(HttpServletRequest r,@RequestParam(required=false) String status,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="12") int size) { return ApiResult.ok(store.records(Actor.from(r),status,page,size)); }
    @PostMapping("/maintenance-records/{id}/complete") public ApiResult<?> complete(HttpServletRequest r,@PathVariable Long id,@Valid @RequestBody Requests.Complete b) { store.complete(Actor.from(r),id,b); return ApiResult.ok(null); }
    @GetMapping("/slots") public ApiResult<?> slots(HttpServletRequest r) { return ApiResult.ok(store.slots(Actor.from(r))); }
    @PostMapping("/slots") public ApiResult<?> createSlot(HttpServletRequest r,@Valid @RequestBody Requests.SlotInput b) { return ApiResult.ok(store.saveSlot(Actor.from(r),null,b)); }
    @PutMapping("/slots/{id}") public ApiResult<?> editSlot(HttpServletRequest r,@PathVariable Long id,@Valid @RequestBody Requests.SlotInput b) { return ApiResult.ok(store.saveSlot(Actor.from(r),id,b)); }
}
