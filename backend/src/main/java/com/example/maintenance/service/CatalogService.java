package com.example.maintenance.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.maintenance.entity.*;
import com.example.maintenance.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CatalogService {
    private final ProductMapper products;
    private final StoreMapper stores;
    private final ReviewMapper reviews;
    private final BusinessViews views;
    public Map<String,Object> products(String keyword, String category, int page, int size, boolean admin) {
        QueryWrapper<Product> query = new QueryWrapper<Product>();
        if (!admin) query.eq("status", "ON_SHELF"); else query.ne("status", "DELETED");
        if (keyword != null && !keyword.isBlank()) query.and(q -> q.like("name", keyword.trim()).or().like("sku",keyword.trim()));
        if (category != null && !category.isBlank()) query.eq("category", category);
        query.orderByDesc("id"); return views.page(products, query, page, size, views::product);
    }
    public Map<String,Object> stores(String keyword, int page, int size) {
        QueryWrapper<Store> query = new QueryWrapper<Store>().eq("audit_status", "APPROVED");
        if (keyword != null && !keyword.isBlank()) query.and(q -> q.like("name",keyword.trim()).or().like("address",keyword.trim()));
        query.orderByAsc("id"); return views.page(stores, query, page, size, s -> Map.of("id",s.getId(),"name",s.getName(),"address",s.getAddress(),"businessHours",s.getBusinessHours(),"contactPhone",s.getContactPhone()));
    }
    public Map<String,Object> reviews(String type, Long targetId, int page, int size) {
        QueryWrapper<Review> query = new QueryWrapper<Review>().eq("target_type",type).eq("target_id",targetId).orderByDesc("id");
        return views.page(reviews, query, page, size, r -> Map.of("id",r.getId(),"rating",r.getRating(),"content",r.getContent(),"createdAt",r.getCreatedAt(),"author","车主" + r.getUserId()));
    }
}
