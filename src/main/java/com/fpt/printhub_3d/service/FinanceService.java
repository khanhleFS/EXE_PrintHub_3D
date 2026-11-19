package com.fpt.printhub_3d.service;

import com.fpt.printhub_3d.dto.finance.CommissionFundResponseDTO;
import com.fpt.printhub_3d.dto.finance.FinanceSummaryResponseDTO;
import com.fpt.printhub_3d.dto.finance.FinancialReportPeriod;
import com.fpt.printhub_3d.dto.finance.RevenueAnalyticsResponseDTO;

import java.time.Instant;

public interface FinanceService {
    CommissionFundResponseDTO getCommissionFund();

    RevenueAnalyticsResponseDTO getRevenueAnalytics(Instant from, Instant to);

    default FinanceSummaryResponseDTO getFinanceSummary() {
        return getFinanceSummary(new FinancialReportPeriod(null, null));
    }

    FinanceSummaryResponseDTO getFinanceSummary(FinancialReportPeriod period);
}
