package com.fpt.printhub_3d.job;

import com.fpt.printhub_3d.entity.Order;
import com.fpt.printhub_3d.entity.Payment;
import com.fpt.printhub_3d.repository.OrderRepository;
import com.fpt.printhub_3d.repository.PaymentRepository;
import com.fpt.printhub_3d.service.InventoryService;
import com.fpt.printhub_3d.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderExpiryJob {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final InventoryService inventoryService;
    private final NotificationService notificationService;

    /**
     * Chạy định kỳ mỗi phút một lần.
     * Quét các đơn hàng PAYOS đang PENDING mà quá hạn thanh toán 15 phút
     * để tự động hủy (EXPIRED) và giải phóng tồn kho (ACTIVE -> RELEASED).
     */
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void expirePendingOrders() {
        try {
            Instant now = Instant.now();
            List<Order> pendingOrders = orderRepository.findAll().stream()
                    .filter(o -> "PENDING".equals(o.getStatus()))
                    .filter(o -> {
                        if (o.getPaymentDueAt() != null) {
                            return o.getPaymentDueAt().isBefore(now);
                        }
                        // Fallback: nếu paymentDueAt null thì tính quá 15 phút kể từ lúc tạo
                        return o.getCreatedAt() != null && o.getCreatedAt().isBefore(now.minus(15, ChronoUnit.MINUTES));
                    })
                    .toList();

            if (pendingOrders.isEmpty()) {
                return;
            }

            log.info("[OrderExpiryJob] Tìm thấy {} đơn hàng PENDING quá hạn thanh toán. Đang xử lý giải phóng tồn kho...", pendingOrders.size());

            for (Order order : pendingOrders) {
                Payment payment = paymentRepository.findByOrderId(order.getId()).orElse(null);
                if (payment != null && "SUCCESS".equals(payment.getStatus())) {
                    log.info("[OrderExpiryJob] Đơn [{}] đã thanh toán thành công, bỏ qua expire.", order.getId());
                    continue;
                }

                // Cập nhật trạng thái đơn sang EXPIRED
                order.setStatus("EXPIRED");
                order.setUpdatedAt(now);
                orderRepository.save(order);

                // Hủy payment nếu còn PENDING
                if (payment != null && "PENDING".equals(payment.getStatus())) {
                    payment.setStatus("EXPIRED");
                    payment.setUpdatedAt(now);
                    paymentRepository.save(payment);
                }

                // Giải phóng giữ chỗ tồn kho (ACTIVE -> RELEASED, hoàn lại Product.stock)
                inventoryService.releaseActiveReservations(order.getId(), "Hết hạn thanh toán 15 phút", "SYSTEM_EXPIRE_JOB");

                log.info("[OrderExpiryJob] Đã đánh dấu EXPIRED và hoàn tồn kho cho đơn [{}]", order.getId());

                try {
                    notificationService.sendNotification(
                            order.getBuyer(),
                            "Đơn hàng hết hạn thanh toán",
                            "Đơn hàng " + order.getId() + " đã quá hạn 15 phút và tự động được giải phóng tồn kho.",
                            "ORDER",
                            "/orders"
                    );
                } catch (Exception notifEx) {
                    log.warn("[OrderExpiryJob] Không thể gửi thông báo cho buyer [{}]: {}", order.getBuyer().getId(), notifEx.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("[OrderExpiryJob] Lỗi trong tiến trình quét đơn hết hạn: {}", e.getMessage(), e);
        }
    }
}
