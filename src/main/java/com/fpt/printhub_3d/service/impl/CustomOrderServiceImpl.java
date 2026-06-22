package com.fpt.printhub_3d.service.impl;

import com.fpt.printhub_3d.common.exception.ApiException;
import com.fpt.printhub_3d.common.exception.CustomPrintErrorCode;
import com.fpt.printhub_3d.dto.custom_prints.CustomOrderResponseDTO;
import com.fpt.printhub_3d.entity.CustomOrder;
import com.fpt.printhub_3d.entity.Enumeration.CustomOrderStatus;
import com.fpt.printhub_3d.entity.Enumeration.UserRole;
import com.fpt.printhub_3d.entity.User;
import com.fpt.printhub_3d.repository.CustomOrderRepository;
import com.fpt.printhub_3d.repository.UserRepository;
import com.fpt.printhub_3d.service.CustomOrderService;
import com.fpt.printhub_3d.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOrderServiceImpl implements CustomOrderService {

    private final UserRepository userRepository;
    private final CustomOrderRepository customOrderRepository;
    private final FileStorageService fileStorageService;

    @Override
    @Transactional
    public CustomOrderResponseDTO createRequest(UUID makerId, String requirements, MultipartFile file, User buyer) {
        log.info("Buyer [{}] đang tạo yêu cầu in custom đến Maker [{}]", buyer.getId(), makerId);

        // 1. Xác thực Maker tồn tại và có vai trò phù hợp
        User maker = userRepository.findById(makerId)
                .orElseThrow(() -> new ApiException(CustomPrintErrorCode.MAKER_NOT_FOUND));

        if (maker.getRole() != UserRole.MAKER) {
            throw new ApiException(CustomPrintErrorCode.MAKER_INVALID_ROLE);
        }

        // 2. Lưu trữ file thiết kế hình học (.STL)
        String attachmentUrl = fileStorageService.storeFile(file);

        // 3. Khởi tạo và lưu yêu cầu in Custom
        CustomOrder customOrder = new CustomOrder();
        customOrder.setBuyer(buyer);
        customOrder.setMaker(maker);
        customOrder.setRequirements(requirements);
        customOrder.setAttachmentUrl(attachmentUrl);
        customOrder.setStatus(CustomOrderStatus.REQUESTED.name());
        customOrder.setCreatedAt(Instant.now());
        customOrder.setUpdatedAt(Instant.now());

        CustomOrder saved = customOrderRepository.save(customOrder);

        log.info("Tạo yêu cầu in custom thành công. ID đơn: {}", saved.getId());

        return mapToResponseDTO(saved);
    }

    private CustomOrderResponseDTO mapToResponseDTO(CustomOrder entity) {
        return CustomOrderResponseDTO.builder()
                .id(entity.getId())
                .buyerId(entity.getBuyer().getId())
                .buyerName(entity.getBuyer().getFullName())
                .makerId(entity.getMaker().getId())
                .makerName(entity.getMaker().getFullName())
                .requirements(entity.getRequirements())
                .attachmentUrl(entity.getAttachmentUrl())
                .quotedPrice(entity.getQuotedPrice())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
