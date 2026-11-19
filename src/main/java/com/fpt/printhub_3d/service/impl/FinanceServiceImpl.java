package com.fpt.printhub_3d.service.impl;

import com.fpt.printhub_3d.dto.finance.CommissionFundResponseDTO;
import com.fpt.printhub_3d.dto.finance.FinanceSummaryResponseDTO;
import com.fpt.printhub_3d.dto.finance.FinancialReportPeriod;
import com.fpt.printhub_3d.dto.finance.RevenueAnalyticsResponseDTO;
import com.fpt.printhub_3d.entity.Order;
import com.fpt.printhub_3d.entity.Payment;
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
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FinanceServiceImpl implements FinanceService {

    private static final BigDecimal COMMISSION_FUND_RATE = new BigDecimal("0.01");
    private static final BigDecimal PLATFORM_COMMISSION_PERCENT = new BigDecimal("0.05");

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PointWalletRepository pointWalletRepository;
    private final FinancialReportingService financialReporting;

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
        var financialSummary = financialReporting.summarize(new FinancialReportPeriod(from, to));
        BigDecimal orderCommissionRevenue;
        if (from != null && to != null) {
            orderCommissionRevenue = orderRepository.sumCommissionFeeBetween(from, to);
        } else if (from != null) {
            orderCommissionRevenue = orderRepository.sumCommissionFeeFrom(from);
        } else if (to != null) {
            orderCommissionRevenue = orderRepository.sumCommissionFeeBefore(to);
        } else {
            orderCommissionRevenue = orderRepository.sumCommissionFee();
        }
        if (orderCommissionRevenue == null) {
            orderCommissionRevenue = BigDecimal.ZERO;
        }
        BigDecimal customerSubscriptionRevenue = financialSummary.subscriptionCashVnd();

        return RevenueAnalyticsResponseDTO.builder()
                .from(from)
                .to(to)
                .orderCommissionRevenue(orderCommissionRevenue)
                .customerSubscriptionRevenue(customerSubscriptionRevenue)
                .totalRevenue(orderCommissionRevenue.add(customerSubscriptionRevenue))
                .financialSummary(financialSummary)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public FinanceSummaryResponseDTO getFinanceSummary(FinancialReportPeriod period) {
        var financialSummary = financialReporting.summarize(period);
        List<Order> orders = orderRepository.findAll();
        BigDecimal marketplaceGmv = BigDecimal.ZERO;
        BigDecimal totalCommission = BigDecimal.ZERO;

        for (Order o : orders) {
            if ("PAID".equals(o.getStatus()) || "COMPLETED".equals(o.getStatus())) {
                if (o.getTotalAmount() != null) {
                    marketplaceGmv = marketplaceGmv.add(o.getTotalAmount());
                }
                if (o.getCommissionFee() != null) {
                    totalCommission = totalCommission.add(o.getCommissionFee());
                } else if (o.getTotalAmount() != null) {
                    totalCommission = totalCommission.add(o.getTotalAmount().multiply(PLATFORM_COMMISSION_PERCENT));
                }
            }
        }

        BigDecimal factoryPayout = marketplaceGmv.subtract(totalCommission);
        if (factoryPayout.compareTo(BigDecimal.ZERO) < 0) {
            factoryPayout = BigDecimal.ZERO;
        }

        long studentPointSum = pointWalletRepository.findAll().stream()
                .mapToLong(w -> w.getBalance() != null ? w.getBalance() : 0)
                .sum();
        BigDecimal studentWalletBalance = BigDecimal.valueOf(studentPointSum);

        List<Payment> payments = paymentRepository.findAll().stream()
                .filter(payment -> period.includes(transactionDate(payment)))
                .sorted(Comparator.comparing(this::transactionDate).reversed().thenComparing(Payment::getId))
                .limit(20)
                .toList();

        List<FinanceSummaryResponseDTO.FinancialTransactionDTO> txList = new ArrayList<>();
        for (Payment p : payments) {
            String userName = "Khách hàng";
            String type = "Thanh toán " + (p.getGateway() != null ? p.getGateway() : "Cổng sàn");

            if (p.getCheckoutOrder() != null && p.getCheckoutOrder().getUser() != null) {
                userName = p.getCheckoutOrder().getUser().getFullName();
                type = "Checkout #" + p.getCheckoutOrder().getId().toString().substring(0, 8);
            } else if (p.getOrder() != null && p.getOrder().getBuyer() != null) {
                userName = p.getOrder().getBuyer().getFullName();
                type = "Đơn hàng #" + p.getOrder().getId().toString().substring(0, 8);
            } else if (p.getCustomOrder() != null && p.getCustomOrder().getBuyer() != null) {
                userName = p.getCustomOrder().getBuyer().getFullName();
                type = "Đơn custom #" + p.getCustomOrder().getId().toString().substring(0, 8);
            } else if (p.getSubscriptionPurchase() != null && p.getSubscriptionPurchase().getUser() != null) {
                userName = p.getSubscriptionPurchase().getUser().getFullName();
                type = "Gói hội viên " + p.getSubscriptionPurchase().getPlan().getName();
            }

            txList.add(FinanceSummaryResponseDTO.FinancialTransactionDTO.builder()
                    .id(p.getTransactionId() != null ? p.getTransactionId() : "TXN-" + p.getId().toString().substring(0, 8))
                    .user(userName)
                    .type(type)
                    .amount(Payment.PENDING_RECONCILIATION.equals(p.getStatus()) ? p.getReconciliationAmount() : p.getAmount())
                    .status("SUCCESS".equalsIgnoreCase(p.getStatus()) || "PAID".equalsIgnoreCase(p.getStatus()) ? "Thành công" : p.getStatus())
                    .gateway(p.getGateway())
                    .date(transactionDate(p))
                    .build());
        }

        return FinanceSummaryResponseDTO.builder()
                .totalGmv(financialSummary.completedGoodsValueVnd())
                .platformCommission(totalCommission)
                .factoryPayout(factoryPayout)
                .studentWalletBalance(studentWalletBalance)
                .totalOrders((long) orders.size())
                .recentTransactions(txList)
                .financialSummary(financialSummary)
                .build();
    }

    private Instant transactionDate(Payment payment) {
        if (payment.getPaidAt() != null) {
            return payment.getPaidAt();
        }
        return payment.getReconciliationReceivedAt() != null ? payment.getReconciliationReceivedAt() : payment.getCreatedAt();
    }
}
