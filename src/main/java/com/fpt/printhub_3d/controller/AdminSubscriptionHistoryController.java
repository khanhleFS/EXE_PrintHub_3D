package com.fpt.printhub_3d.controller;

import com.fpt.printhub_3d.common.response.ApiResponse;
import com.fpt.printhub_3d.dto.finance.FinancialReportPeriod;
import com.fpt.printhub_3d.dto.subscription.AdminSubscriptionHistoryDTO;
import com.fpt.printhub_3d.dto.subscription.AdminSubscriptionHistoryDTO.Method;
import com.fpt.printhub_3d.dto.subscription.AdminSubscriptionHistoryDTO.Status;
import com.fpt.printhub_3d.service.impl.AdminSubscriptionHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@CrossOrigin("*")
public class AdminSubscriptionHistoryController {
    private final AdminSubscriptionHistoryService history;

    @Operation(summary = "Lịch sử giao dịch hội viên", security = @SecurityRequirement(name = "Bearer Authentication"))
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/api/admin/subscriptions/transactions")
    public ResponseEntity<ApiResponse<AdminSubscriptionHistoryDTO>> getTransactions(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) UUID planId,
            @RequestParam(required = false) Method method,
            @RequestParam(required = false) Status status,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var result = history.getHistory(q, userId, planId, method, status,
                FinancialReportPeriod.parse(from, to), page, size);
        return ResponseEntity.ok(ApiResponse.<AdminSubscriptionHistoryDTO>builder()
                .code(200).message("Lấy lịch sử giao dịch hội viên thành công").result(result).build());
    }
}
