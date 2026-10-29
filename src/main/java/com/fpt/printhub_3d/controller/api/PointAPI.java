package com.fpt.printhub_3d.controller.api;

import com.fpt.printhub_3d.common.response.ApiResponse;
import com.fpt.printhub_3d.dto.point.PointTransactionResponseDTO;
import com.fpt.printhub_3d.dto.point.PointWalletResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;

@Tag(name = "Point Wallet APIs", description = "Các API truy vấn số dư ví điểm thưởng và lịch sử giao dịch điểm tích lũy")
public interface PointAPI {

    @Operation(
            summary = "Lấy thông tin số dư ví điểm của người dùng hiện tại",
            description = "Trả về số dư điểm khả dụng và thời điểm cập nhật mới nhất từ ví điểm",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @GetMapping("/api/points/me")
    ResponseEntity<ApiResponse<PointWalletResponseDTO>> getMyWallet();

    @Operation(
            summary = "Lấy lịch sử biến động điểm thưởng (Ledger Audit) của người dùng hiện tại",
            description = "Trả về danh sách phân trang các giao dịch tích điểm (EARN) và đổi điểm (REDEEM)",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @GetMapping("/api/points/me/transactions")
    ResponseEntity<ApiResponse<Page<PointTransactionResponseDTO>>> getMyTransactions(Pageable pageable);
}

