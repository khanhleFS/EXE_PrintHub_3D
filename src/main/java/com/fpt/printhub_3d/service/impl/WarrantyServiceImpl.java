package com.fpt.printhub_3d.service.impl;

import com.fpt.printhub_3d.common.exception.ApiException;
import com.fpt.printhub_3d.common.exception.CommonErrorCode;
import com.fpt.printhub_3d.dto.warranty.WarrantyClaimRequestDTO;
import com.fpt.printhub_3d.dto.warranty.WarrantyClaimResponseDTO;
import com.fpt.printhub_3d.dto.warranty.WarrantyClaimStatusRequestDTO;
import com.fpt.printhub_3d.entity.Order;
import com.fpt.printhub_3d.entity.User;
import com.fpt.printhub_3d.entity.WarrantyClaim;
import com.fpt.printhub_3d.repository.OrderRepository;
import com.fpt.printhub_3d.repository.WarrantyClaimRepository;
import com.fpt.printhub_3d.service.NotificationService;
import com.fpt.printhub_3d.service.WarrantyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class WarrantyServiceImpl implements WarrantyService {

    private final WarrantyClaimRepository warrantyClaimRepository;
    private final OrderRepository orderRepository;
    private final NotificationService notificationService;

    private WarrantyClaimResponseDTO toDTO(WarrantyClaim c) {
        return WarrantyClaimResponseDTO.builder()
                .id(c.getId())
                .orderId(c.getOrderId())
                .buyerName(c.getUser().getFullName())
                .description(c.getReason())
                .imageUrl(c.getImageProofUrl())
                .status(c.getStatus())
                .createdAt(c.getCreatedAt())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<WarrantyClaimResponseDTO> getClaimsByUser(User user) {
        return warrantyClaimRepository.findByUserId(user.getId())
                .stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(this::toDTO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<WarrantyClaimResponseDTO> getAllClaims() {
        return warrantyClaimRepository.findAll()
                .stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(this::toDTO)
                .toList();
    }

    @Override
    public WarrantyClaimResponseDTO createClaim(User user, WarrantyClaimRequestDTO request) {
        Order order = orderRepository.findById(request.orderId())
                .orElseThrow(() -> new ApiException(CommonErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đơn hàng"));

        if (!order.getBuyer().getId().equals(user.getId())) {
            throw new ApiException(CommonErrorCode.FORBIDDEN, "Đơn hàng không thuộc tài khoản của bạn");
        }
        if (!"COMPLETED".equals(order.getStatus())) {
            throw new ApiException(CommonErrorCode.INVALID_INPUT, "Chỉ có thể yêu cầu bảo hành cho đơn hàng đã hoàn thành (COMPLETED)");
        }

        WarrantyClaim claim = new WarrantyClaim();
        claim.setOrderId(order.getId());
        claim.setUser(user);
        claim.setReason(request.description());
        claim.setImageProofUrl(request.imageUrl());
        claim.setStatus("PENDING");
        claim.setCreatedAt(Instant.now());
        claim.setUpdatedAt(Instant.now());

        warrantyClaimRepository.save(claim);
        return toDTO(claim);
    }

    @Override
    public void updateClaimStatus(UUID id, WarrantyClaimStatusRequestDTO request, User admin) {
        WarrantyClaim claim = warrantyClaimRepository.findById(id)
                .orElseThrow(() -> new ApiException(CommonErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy yêu cầu bảo hành"));

        String current = claim.getStatus();
        String next = request.status().toUpperCase();

        boolean validTransition = ("PENDING".equals(current) && Set.of("APPROVED", "REJECTED").contains(next))
                || ("APPROVED".equals(current) && "REPLACED".equals(next));

        if (!validTransition) {
            throw new ApiException(CommonErrorCode.INVALID_INPUT, "Trạng thái bảo hành chuyển đổi không hợp lệ từ " + current + " sang " + next);
        }

        claim.setStatus(next);
        claim.setUpdatedAt(Instant.now());
        warrantyClaimRepository.save(claim);

        notificationService.sendNotification(claim.getUser(),
                "Cập nhật bảo hành",
                "Yêu cầu bảo hành " + id + " đã cập nhật: " + next,
                "WARRANTY",
                "/warranty");
    }
}
