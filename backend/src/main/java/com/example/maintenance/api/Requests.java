package com.example.maintenance.api;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class Requests {
    private Requests() {}
    public record RegisterCustomer(
        @NotBlank @Pattern(regexp="[A-Za-z0-9_]{3,50}", message="登录名需为3至50位字母、数字或下划线") String loginName,
        @NotBlank @Pattern(regexp="1[3-9][0-9]{9}", message="请输入有效的11位手机号") String phone,
        @NotBlank @Size(min=8,max=72,message="密码长度需为8至72位") String password) {}
    public record RegisterStore(
        @NotBlank @Pattern(regexp="[A-Za-z0-9_]{3,50}", message="登录名需为3至50位字母、数字或下划线") String loginName,
        @NotBlank @Pattern(regexp="1[3-9][0-9]{9}", message="请输入有效的11位手机号") String phone,
        @NotBlank @Size(min=8,max=72,message="密码长度需为8至72位") String password,
        @NotBlank @Size(max=100) String name, @NotBlank @Size(max=50) String contactName,
        @NotBlank @Pattern(regexp="1[3-9][0-9]{9}", message="联系人手机号格式不正确") String contactPhone,
        @NotBlank @Size(max=255) String address, @NotBlank @Size(max=100) String businessHours) {}
    public record Login(@NotBlank @Size(max=50) String loginName, @NotBlank @Size(max=72) String password) {}
    public record CartAdd(@NotNull @Positive Long productId, @NotNull @Min(1) @Max(99) Integer quantity) {}
    public record Quantity(@NotNull @Min(1) @Max(99) Integer quantity) {}
    public record CreateOrder(@NotEmpty @Size(max=50) List<@NotNull @Positive Long> cartItemIds, @NotNull @Positive Long storeId) {}
    public record Decision(@NotBlank @Pattern(regexp="APPROVED|REJECTED",message="审核结论不正确") String decision, @Size(max=500) String comment) {}
    public record ProductInput(
        @NotBlank @Size(max=50) String sku, @NotBlank @Size(max=120) String name,
        @NotBlank @Size(max=80) String category, @Size(max=2000) String description, @Size(max=500) String imageUrl,
        @NotNull @DecimalMin("0") @DecimalMax("999999.99") @Digits(integer=6,fraction=2) BigDecimal price,
        @NotNull @DecimalMin("0") @DecimalMax("999999.99") @Digits(integer=6,fraction=2) BigDecimal laborFee,
        @NotBlank @Pattern(regexp="ON_SHELF|OFF_SHELF") String status, @NotNull @Min(0) @Max(1000000) Integer stock) {}
    public record Booking(@NotNull @Positive Long orderId, @NotNull @Positive Long slotId) {}
    public record Code(@NotBlank @Pattern(regexp="[0-9]{10}",message="请输入十位数字凭据") String code) {}
    public record Arrival(@NotNull @Positive Long storeId, @NotBlank @Pattern(regexp="[0-9]{10}",message="请输入十位数字凭据") String code) {}
    public record Complete(@NotBlank @Size(max=2000) String serviceItems, @Size(max=2000) String note) {}
    public record SlotInput(@NotNull LocalDate serviceDate,
        @NotBlank @Pattern(regexp="([01][0-9]|2[0-3]):[0-5][0-9]") String startTime,
        @NotBlank @Pattern(regexp="([01][0-9]|2[0-3]):[0-5][0-9]") String endTime,
        @NotNull @Min(1) @Max(100) Integer capacity,
        @NotBlank @Pattern(regexp="OPEN|CLOSED") String status) {}
    public record ReviewInput(@NotNull @Positive Long orderId,
        @NotBlank @Pattern(regexp="PRODUCT|STORE") String targetType, @NotNull @Positive Long targetId,
        @NotNull @Min(1) @Max(5) Integer rating, @NotBlank @Size(max=1000) String content) {}
}
