package com.fpt.printhub_3d.service;

import com.fpt.printhub_3d.dto.dashboard.DashboardResponseDTO;
import com.fpt.printhub_3d.dto.finance.FinancialReportPeriod;
import com.fpt.printhub_3d.entity.User;

public interface DashboardService {
    default DashboardResponseDTO getDashboard(User user, boolean all) {
        return getDashboard(user, all, new FinancialReportPeriod(null, null));
    }

    DashboardResponseDTO getDashboard(User user, boolean all, FinancialReportPeriod period);
}
