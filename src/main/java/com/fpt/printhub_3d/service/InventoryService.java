package com.fpt.printhub_3d.service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public interface InventoryService {

    /**
     * Giữ chỗ tồn kho cho đơn hàng mới (ACTIVE) và trừ Product.stock đúng 1 lần.
     * Khóa products theo thứ tự UUID tăng dần để chống deadlock.
     *
     * @param orderId ID đơn hàng
     * @param productQuantities Map chứa productId và số lượng cần mua
     * @param operationId ID của command/request chống lặp
     * @param expiresAt Thời hạn giữ chỗ (ví dụ: now + 15 phút cho PAYOS)
     */
    void reserveForOrder(UUID orderId, Map<UUID, Integer> productQuantities, String operationId, Instant expiresAt);

    /**
     * Xác nhận giữ chỗ (ACTIVE -> CONFIRMED) khi thanh toán thành công hoặc bắt đầu sản xuất COD.
     * Tồn kho không đổi vì đã được trừ lúc ACTIVE. Idempotent nếu gọi lại.
     *
     * @param orderId ID đơn hàng
     */
    void confirmReservation(UUID orderId);

    /**
     * Giải phóng giữ chỗ và hoàn lại tồn kho (ACTIVE -> RELEASED) khi đơn bị hủy hoặc hết hạn.
     * Hoàn lại Product.stock đúng 1 lần. Idempotent nếu gọi lại.
     *
     * @param orderId ID đơn hàng
     * @param reason Lý do giải phóng (Hủy đơn, Hết hạn thanh toán,...)
     * @param operationId ID của command/request
     */
    void releaseActiveReservations(UUID orderId, String reason, String operationId);
}
