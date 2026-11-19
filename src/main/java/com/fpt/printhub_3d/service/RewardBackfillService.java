package com.fpt.printhub_3d.service;

import com.fpt.printhub_3d.dto.order.RewardBackfillReportDTO;

/**
 * Dịch vụ đối soát và cộng bù xu cho các đơn hàng cũ đã hoàn tất (XU-09, E2E-04).
 * Hỗ trợ chế độ dryRun=true (báo cáo thử không ghi DB) và dryRun=false (cộng bù có khóa riêng, idempotent).
 */
public interface RewardBackfillService {
    RewardBackfillReportDTO runBackfill(boolean dryRun);
}
