package com.fpt.printhub_3d.service.impl;

import com.fpt.printhub_3d.dto.finance.CommissionFundResponseDTO;
import com.fpt.printhub_3d.dto.finance.FinanceSummaryResponseDTO;
import com.fpt.printhub_3d.dto.finance.RevenueAnalyticsResponseDTO;
import com.fpt.printhub_3d.entity.Order;
import com.fpt.printhub_3d.entity.Payment;
import com.fpt.printhub_3d.entity.PointWallet;
import com.fpt.printhub_3d.repository.OrderRepository;
import com.fpt.printhub_3d.repository.PaymentRepository;
import com.fpt.printhub_3d.repository.PointWalletRepository;
import com.fpt.printhub_3d.service.FinanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FinanceServiceImpl implements FinanceService {

    private static final BigDecimal COMMISSION_FUND_RATE = new BigDecimal("0.01");
    private static final BigDecimal PLATFORM_COMMISSION_PERCENT = new BigDecimal("0.05");

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PointWalletRepository pointWalletRepository;

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
        BigDecimal customerSubscriptionRevenue = BigDecimal.ZERO;

        return RevenueAnalyticsResponseDTO.builder()
                .from(from)
                .to(to)
                .orderCommissionRevenue(orderCommissionRevenue)
                .customerSubscriptionRevenue(customerSubscriptionRevenue)
                .totalRevenue(orderCommissionRevenue
                        .add(customerSubscriptionRevenue))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public FinanceSummaryResponseDTO getFinanceSummary() {
        List<Order> orders = orderRepository.findAll();
        BigDecimal totalGmv = BigDecimal.ZERO;
        BigDecimal totalCommission = BigDecimal.ZERO;

        for (Order o : orders) {
            if ("PAID".equals(o.getStatus()) || "COMPLETED".equals(o.getStatus())) {
                if (o.getTotalAmount() != null) {
                    totalGmv = totalGmv.add(o.getTotalAmount());
                }
                if (o.getCommissionFee() != null) {
                    totalCommission = totalCommission.add(o.getCommissionFee());
                } else if (o.getTotalAmount() != null) {
                    totalCommission = totalCommission.add(o.getTotalAmount().multiply(PLATFORM_COMMISSION_PERCENT));
                }
            }
        }

        BigDecimal factoryPayout = totalGmv.subtract(totalCommission);
        if (factoryPayout.compareTo(BigDecimal.ZERO) < 0) {
            factoryPayout = BigDecimal.ZERO;
        }

        long studentPointSum = pointWalletRepository.findAll().stream()
                .mapToLong(w -> w.getBalance() != null ? w.getBalance() : 0)
                .sum();
        BigDecimal studentWalletBalance = BigDecimal.valueOf(studentPointSum);

        List<Payment> payments = paymentRepository.findAll().stream()
                .sorted((a, b) -> {
                    Instant tA = a.getCreatedAt() != null ? a.getCreatedAt() : Instant.EPOCH;
                    Instant tB = b.getCreatedAt() != null ? b.getCreatedAt() : Instant.EPOCH;
                    return tB.compareTo(tA);
                })
                .limit(20)
                .toList();

        List<FinanceSummaryResponseDTO.FinancialTransactionDTO> txList = new ArrayList<>();
        for (Payment p : payments) {
            String userName = "Khách hàng";
            String type = "Thanh toán " + (p.getGateway() != null ? p.getGateway() : "Cổng sàn");

            if (p.getOrder() != null && p.getOrder().getBuyer() != null) {
                userName = p.getOrder().getBuyer().getFullName();
                type = "Đơn hàng #" + p.getOrder().getId().toString().substring(0, 8);
            } else if (p.getCustomOrder() != null && p.getCustomOrder().getBuyer() != null) {
                userName = p.getCustomOrder().getBuyer().getFullName();
                type = "Đơn custom #" + p.getCustomOrder().getId().toString().substring(0, 8);
            }

            txList.add(FinanceSummaryResponseDTO.FinancialTransactionDTO.builder()
                    .id(p.getTransactionId() != null ? p.getTransactionId() : "TXN-" + p.getId().toString().substring(0, 8))
                    .user(userName)
                    .type(type)
                    .amount(p.getAmount())
                    .status("SUCCESS".equalsIgnoreCase(p.getStatus()) || "PAID".equalsIgnoreCase(p.getStatus()) ? "Thành công" : p.getStatus())
                    .gateway(p.getGateway())
                    .date(p.getPaidAt() != null ? p.getPaidAt() : p.getCreatedAt())
                    .build());
        }

        return FinanceSummaryResponseDTO.builder()
                .totalGmv(totalGmv)
                .platformCommission(totalCommission)
                .factoryPayout(factoryPayout)
                .studentWalletBalance(studentWalletBalance)
                .totalOrders((long) orders.size())
                .recentTransactions(txList)
                .build();
    }
}
