package com.fpt.printhub_3d.controller.api;

import com.fpt.printhub_3d.common.response.ApiResponse;
import com.fpt.printhub_3d.dto.order.RewardBackfillReportDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "Admin Reward Backfill APIs", description = "Đối soát và cộng bù xu cho các đơn hàng cũ đã hoàn tất")
public interface AdminRewardAPI {

    @Operation(
            summary = "Run reward points reconciliation & backfill (Admin)",
            description = "Mặc định dryRun=true chỉ báo cáo đối soát, không thay đổi ví. Truyền dryRun=false để thực thi cộng bù có khóa chống trùng.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @PostMapping("/api/admin/rewards/backfill")
    ResponseEntity<ApiResponse<RewardBackfillReportDTO>> runRewardBackfill(
            @RequestParam(name = "dryRun", defaultValue = "true") boolean dryRun
    );
}
