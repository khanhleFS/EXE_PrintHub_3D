package com.fpt.printhub_3d.service.impl;

import com.fpt.printhub_3d.dto.finance.CommissionFundResponseDTO;
import com.fpt.printhub_3d.dto.finance.RevenueAnalyticsResponseDTO;
import com.fpt.printhub_3d.repository.OrderRepository;
import com.fpt.printhub_3d.service.FinanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class FinanceServiceImpl implements FinanceService {

    private static final BigDecimal COMMISSION_FUND_RATE = new BigDecimal("0.01");

    private final OrderRepository orderRepository;

    @Override
    @Transactional(readOnly = true)
    public CommissionFundResponseDTO getCommissionFund() {
        BigDecimal totalCommissionFee = orderRepository.sumCommissionFee();
        BigDecimal fundBalance = totalCommissionFee.multiply(COMMISSION_FUND_RATE);

        return CommissionFundResponseDTO.builder()
                .totalCommissionFee(totalCommissionFee)
                .fundAllocationRate(COMMISSION_FUND_RATE)
                .commissionFundBalance(fundBalance)
                .totalOrders(orderRepository.countOrders())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public RevenueAnalyticsResponseDTO getRevenueAnalytics(Instant from, Instant to) {
        BigDecimal orderCommissionRevenue = orderRepository.sumCommissionFeeBetween(from, to);
        BigDecimal makerSubscriptionRevenue = BigDecimal.ZERO;
        BigDecimal customerSubscriptionRevenue = BigDecimal.ZERO;

        return RevenueAnalyticsResponseDTO.builder()
                .from(from)
                .to(to)
                .orderCommissionRevenue(orderCommissionRevenue)
                .makerSubscriptionRevenue(makerSubscriptionRevenue)
                .customerSubscriptionRevenue(customerSubscriptionRevenue)
                .totalRevenue(orderCommissionRevenue
                        .add(makerSubscriptionRevenue)
                        .add(customerSubscriptionRevenue))
                .build();
    }
}
