package com.fpt.printhub_3d.controller;

import com.fpt.printhub_3d.common.response.ApiResponse;
import com.fpt.printhub_3d.controller.api.AdminRewardAPI;
import com.fpt.printhub_3d.dto.order.RewardBackfillReportDTO;
import com.fpt.printhub_3d.service.RewardBackfillService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@CrossOrigin("*")
public class AdminRewardController implements AdminRewardAPI {

    private final RewardBackfillService rewardBackfillService;

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<RewardBackfillReportDTO>> runRewardBackfill(boolean dryRun) {
        RewardBackfillReportDTO report = rewardBackfillService.runBackfill(dryRun);
        String msg = dryRun
                ? "Chạy báo cáo thử đối soát cộng bù xu thành công"
                : "Thực thi cộng bù xu hoàn tất (tổng cộng thêm " + report.totalPointsAdded() + " xu)";
        return ResponseEntity.ok(ApiResponse.<RewardBackfillReportDTO>builder()
                .code(200)
                .message(msg)
                .result(report)
                .build());
    }
}
