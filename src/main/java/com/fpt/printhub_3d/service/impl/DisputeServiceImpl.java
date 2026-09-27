package com.fpt.printhub_3d.service.impl;

import com.fpt.printhub_3d.common.exception.ApiException;
import com.fpt.printhub_3d.common.exception.CommonErrorCode;
import com.fpt.printhub_3d.common.exception.ReviewDisputeErrorCode;
import com.fpt.printhub_3d.dto.dispute.*;
import com.fpt.printhub_3d.entity.*;
import com.fpt.printhub_3d.entity.Enumeration.UserRole;
import com.fpt.printhub_3d.repository.DisputeRepository;
import com.fpt.printhub_3d.repository.DisputeResponseRepository;
import com.fpt.printhub_3d.repository.OrderRepository;
import com.fpt.printhub_3d.repository.RefundRepository;
import com.fpt.printhub_3d.service.DisputeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DisputeServiceImpl implements DisputeService {

    private final DisputeRepository disputeRepository;
    private final DisputeResponseRepository disputeResponseRepository;
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
        return toResponse(savedDispute, null);
    }

    @Override
    @Transactional
    public DisputeResponseDTO resolveDispute(UUID id, DisputeResolutionRequestDTO request) {
        Dispute dispute = disputeRepository.findLockedById(id)
                .orElseThrow(() -> new ApiException(ReviewDisputeErrorCode.DISPUTE_NOT_FOUND));

        if (!Set.of("OPEN", "UNDER_REVIEW").contains(dispute.getStatus())) {
            throw new ApiException(CommonErrorCode.INVALID_INPUT, "Tranh chấp đã được xử lý");
        }
        String status = request.status().trim().toUpperCase();
        if (request.refundAmount() != null && (request.refundAmount().compareTo(dispute.getOrder().getTotalAmount()) > 0
                || (request.refundAmount().signum() > 0 && !"RESOLVED".equals(status)))) {
            throw new ApiException(ReviewDisputeErrorCode.INVALID_REFUND);
        }
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

        return toResponse(savedDispute, refund);
    }

    private DisputeDetailDTO toDetailDTO(Dispute d) {
        Refund r = refundRepository.findById(d.getId()).orElse(null);
        return DisputeDetailDTO.builder()
                .id(d.getId())
                .orderId(d.getOrder().getId())
                .buyerName(d.getOrder().getBuyer().getFullName())
                .amount(d.getOrder().getTotalAmount())
                .description(d.getDescription())
                .evidenceUrl(d.getEvidenceUrl())
                .status(d.getStatus())
                .resolutionNote(d.getResolutionNote())
                .refundAmount(r != null ? r.getAmount() : null)
                .createdAt(d.getCreatedAt())
                .build();
    }

    private Dispute checkAccess(UUID disputeId, User user) {
        Dispute d = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new ApiException(ReviewDisputeErrorCode.DISPUTE_NOT_FOUND));

        if (user.getRole() != UserRole.ADMIN
                && !d.getOrder().getBuyer().getId().equals(user.getId())
                && !d.getOrder().getSeller().getId().equals(user.getId())) {
            throw new ApiException(CommonErrorCode.FORBIDDEN, "Bạn không có quyền truy cập khiếu nại này");
        }
        return d;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DisputeDetailDTO> getMyDisputes(User user) {
        return disputeRepository.findAll().stream()
                .filter(d -> d.getOrder().getBuyer().getId().equals(user.getId()) || d.getOrder().getSeller().getId().equals(user.getId()))
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(this::toDetailDTO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DisputeDetailDTO> getAllDisputes() {
        return disputeRepository.findAll().stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(this::toDetailDTO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DisputeMessageResponseDTO> getMessages(UUID disputeId, User user) {
        checkAccess(disputeId, user);
        return disputeResponseRepository.findByDisputeIdOrderByCreatedAtAsc(disputeId).stream()
                .map(r -> DisputeMessageResponseDTO.builder()
                        .id(r.getId())
                        .author(r.getRespondedBy().getFullName())
                        .content(r.getContent())
                        .createdAt(r.getCreatedAt())
                        .build())
                .toList();
    }

    @Override
    @Transactional
    public void sendMessage(UUID disputeId, DisputeMessageRequestDTO request, User user) {
        Dispute d = checkAccess(disputeId, user);
        if (!Set.of("OPEN", "UNDER_REVIEW").contains(d.getStatus())) {
            throw new ApiException(CommonErrorCode.INVALID_INPUT, "Tranh chấp này đã đóng, không thể gửi tin nhắn");
        }

        DisputeRespons r = new DisputeRespons();
        r.setDispute(d);
        r.setRespondedBy(user);
        r.setContent(request.content());
        r.setCreatedAt(Instant.now());
        disputeResponseRepository.save(r);
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
