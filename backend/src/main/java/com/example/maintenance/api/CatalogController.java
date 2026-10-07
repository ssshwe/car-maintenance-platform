package com.example.maintenance.api;

import com.example.maintenance.common.ApiResult;
import com.example.maintenance.service.CatalogService;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Validated
public class CatalogController {
    private final CatalogService catalog;
    @GetMapping("/products") public ApiResult<?> products(@RequestParam(required=false) String keyword,@RequestParam(required=false) String category,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="12") int size) { return ApiResult.ok(catalog.products(keyword,category,page,size,false)); }
    @GetMapping("/stores") public ApiResult<?> stores(@RequestParam(required=false) String keyword,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="12") int size) { return ApiResult.ok(catalog.stores(keyword,page,size)); }
    @GetMapping("/reviews") public ApiResult<?> reviews(@RequestParam @Pattern(regexp="PRODUCT|STORE") String targetType,@RequestParam @Positive Long targetId,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="12") int size) { return ApiResult.ok(catalog.reviews(targetType,targetId,page,size)); }
}
