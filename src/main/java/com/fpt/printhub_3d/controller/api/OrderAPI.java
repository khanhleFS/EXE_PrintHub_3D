package com.fpt.printhub_3d.controller.api;

import com.fpt.printhub_3d.common.response.ApiResponse;
import com.fpt.printhub_3d.dto.order.OrderCreateRequestDTO;
import com.fpt.printhub_3d.dto.order.OrderResponseDTO;
import com.fpt.printhub_3d.dto.order.RewardCompletionResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;
import java.util.UUID;

@RequestMapping("/api/orders")
@Tag(name = "Order APIs", description = "APIs for creating and managing orders")
public interface OrderAPI {

    @Operation(
            summary = "Create new marketplace order(s)",
            description = "Khởi tạo đơn hàng mua các sản phẩm bán sẵn thương mại thông thường dựa trên số lượng tồn kho có sẵn của Maker. Có thể mua nhiều sản phẩm từ các Maker khác nhau cùng lúc, hệ thống sẽ tự động tách thành các đơn hàng riêng biệt.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @PostMapping
    ResponseEntity<ApiResponse<List<OrderResponseDTO>>> createOrders(
            @Valid @RequestBody OrderCreateRequestDTO request);

    @Operation(
            summary = "Complete reward points for a completed order",
            description = "Tự động tính và cộng điểm thưởng vào tài khoản Customer khi đơn hàng đã ở trạng thái COMPLETED.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @PostMapping("/{id}/complete-rewards")
    ResponseEntity<ApiResponse<RewardCompletionResponseDTO>> completeRewards(@PathVariable UUID id);
}
