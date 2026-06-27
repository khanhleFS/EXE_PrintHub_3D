package com.fpt.printhub_3d.service.impl;

import com.fpt.printhub_3d.common.exception.ApiException;
import com.fpt.printhub_3d.common.exception.ReviewDisputeErrorCode;
import com.fpt.printhub_3d.dto.dispute.DisputeCreateRequestDTO;
import com.fpt.printhub_3d.dto.dispute.DisputeResolutionRequestDTO;
import com.fpt.printhub_3d.dto.dispute.DisputeResponseDTO;
import com.fpt.printhub_3d.entity.Dispute;
import com.fpt.printhub_3d.entity.Order;
import com.fpt.printhub_3d.entity.Refund;
import com.fpt.printhub_3d.entity.User;
import com.fpt.printhub_3d.repository.DisputeRepository;
import com.fpt.printhub_3d.repository.OrderRepository;
import com.fpt.printhub_3d.repository.RefundRepository;
import com.fpt.printhub_3d.service.DisputeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DisputeServiceImpl implements DisputeService {

    private final DisputeRepository disputeRepository;
    private final OrderRepository orderRepository;
    private final RefundRepository refundRepository;

    @Override
    @Transactional
    public DisputeResponseDTO createDispute(DisputeCreateRequestDTO request, User filedBy) {
        Order order = orderRepository.findById(request.orderId())
                .orElseThrow(() -> new ApiException(ReviewDisputeErrorCode.ORDER_NOT_FOUND));

        boolean isBuyer = order.getBuyer().getId().equals(filedBy.getId());
        boolean isSeller = order.getSeller().getId().equals(filedBy.getId());
        if (!isBuyer && !isSeller) {
            throw new ApiException(ReviewDisputeErrorCode.NOT_ORDER_PARTICIPANT);
        }

        if (disputeRepository.existsByOrderId(order.getId())) {
            throw new ApiException(ReviewDisputeErrorCode.DISPUTE_ALREADY_EXISTS);
        }

        Dispute dispute = new Dispute();
        dispute.setOrder(order);
        dispute.setFiledBy(filedBy);
        dispute.setDescription(request.description());
        dispute.setEvidenceUrl(request.evidenceUrl());
        dispute.setStatus("OPEN");
        dispute.setCreatedAt(Instant.now());
        dispute.setUpdatedAt(Instant.now());

        Dispute savedDispute = disputeRepository.save(dispute);
        log.info("Tạo dispute [{}] cho order [{}]", savedDispute.getId(), order.getId());
        return toResponse(savedDispute, null);
    }

    @Override
    @Transactional
    public DisputeResponseDTO resolveDispute(UUID id, DisputeResolutionRequestDTO request) {
        Dispute dispute = disputeRepository.findById(id)
                .orElseThrow(() -> new ApiException(ReviewDisputeErrorCode.DISPUTE_NOT_FOUND));

        String status = request.status().trim().toUpperCase();
        if (!"RESOLVED".equals(status) && !"REJECTED".equals(status)) {
            throw new ApiException(ReviewDisputeErrorCode.INVALID_RESOLUTION_STATUS);
        }

        Refund refund = null;
        if (request.refundAmount() != null && request.refundAmount().compareTo(BigDecimal.ZERO) > 0) {
            String refundType = request.refundType() == null ? null : request.refundType().trim().toUpperCase();
            if (!"FULL".equals(refundType) && !"PARTIAL".equals(refundType)) {
                throw new ApiException(ReviewDisputeErrorCode.INVALID_REFUND);
            }

            refund = refundRepository.findById(dispute.getId()).orElseGet(Refund::new);
            refund.setId(dispute.getId());
            refund.setDisputes(dispute);
            refund.setAmount(request.refundAmount());
            refund.setType(refundType);
            refund.setIssuedAt(Instant.now());
            refund = refundRepository.save(refund);
        }

        dispute.setStatus(status);
        dispute.setResolutionNote(request.resolutionNote());
        dispute.setUpdatedAt(Instant.now());
        Dispute savedDispute = disputeRepository.save(dispute);

        log.info("Admin xử lý dispute [{}] với trạng thái [{}]", savedDispute.getId(), status);
        return toResponse(savedDispute, refund);
    }

    private DisputeResponseDTO toResponse(Dispute dispute, Refund refund) {
        return DisputeResponseDTO.builder()
                .id(dispute.getId())
                .orderId(dispute.getOrder().getId())
                .filedById(dispute.getFiledBy().getId())
                .filedByName(dispute.getFiledBy().getFullName())
                .description(dispute.getDescription())
                .evidenceUrl(dispute.getEvidenceUrl())
                .status(dispute.getStatus())
                .resolutionNote(dispute.getResolutionNote())
                .refundAmount(refund == null ? null : refund.getAmount())
                .refundType(refund == null ? null : refund.getType())
                .createdAt(dispute.getCreatedAt())
                .updatedAt(dispute.getUpdatedAt())
                .build();
    }
}
