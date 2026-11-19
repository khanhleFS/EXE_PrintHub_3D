package com.fpt.printhub_3d.service.impl;

import com.fpt.printhub_3d.common.exception.ApiException;
import com.fpt.printhub_3d.common.exception.CommonErrorCode;
import com.fpt.printhub_3d.common.exception.VaultErrorCode;
import com.fpt.printhub_3d.dto.custom_prints.CustomOrderCreateRequestDTO;
import com.fpt.printhub_3d.dto.custom_prints.CustomOrderDetailResponseDTO;
import com.fpt.printhub_3d.dto.custom_prints.CustomOrderFileDTO;
import com.fpt.printhub_3d.dto.custom_prints.CustomOrderQuoteRequestDTO;
import com.fpt.printhub_3d.dto.custom_prints.CustomOrderResponseDTO;
import com.fpt.printhub_3d.dto.custom_prints.CustomOrderStatusRequestDTO;
import com.fpt.printhub_3d.dto.order.RewardCompletionResponseDTO;
import com.fpt.printhub_3d.entity.CustomOrder;
import com.fpt.printhub_3d.entity.Enumeration.CustomOrderStatus;
import com.fpt.printhub_3d.entity.Enumeration.OrderRewardStatus;
import com.fpt.printhub_3d.entity.Enumeration.UserRole;
import com.fpt.printhub_3d.entity.FileAsset;
import com.fpt.printhub_3d.entity.Payment;
import com.fpt.printhub_3d.entity.User;
import com.fpt.printhub_3d.repository.CustomOrderRepository;
import com.fpt.printhub_3d.repository.FileAssetRepository;
import com.fpt.printhub_3d.service.CloudinaryService;
import com.fpt.printhub_3d.service.CustomOrderService;
import com.fpt.printhub_3d.service.NotificationService;
import com.fpt.printhub_3d.service.OrderRewardService;
import com.fpt.printhub_3d.service.OrderTransitionPolicy;
import com.fpt.printhub_3d.service.PaymentEvidenceService;
import com.fpt.printhub_3d.service.SubscriptionBenefitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class CustomOrderServiceImpl implements CustomOrderService {

    private final CustomOrderRepository customOrderRepository;
    private final @Nullable FileAssetRepository fileAssetRepository;
    private final @Nullable CloudinaryService cloudinaryService;
    private final OrderTransitionPolicy orderTransitionPolicy;
    private final @Nullable NotificationService notificationService;
    private final @Nullable SubscriptionBenefitService subscriptionBenefitService;
    private final @Nullable OrderRewardService orderRewardService;
    private final CustomOrderMapper customOrderMapper;
    private final CustomOrderQuotationService quotationService;

    @Override
    public CustomOrderResponseDTO createRequest(String requirements, MultipartFile file, User buyer) {
        log.info("Buyer [{}] đang tạo yêu cầu in custom", buyer.getId());

        String attachmentUrl = cloudinaryService.uploadRawFile(file, "printhub3d/custom_prints");

        CustomOrder customOrder = new CustomOrder();
        customOrder.setBuyer(buyer);
        customOrder.setProcessedBy(null);
        customOrder.setRequirements(requirements);
        customOrder.setAttachmentUrl(attachmentUrl);
        customOrder.setStatus(CustomOrderStatus.REQUESTED.name());
        customOrder.setCreatedAt(Instant.now());
        customOrder.setUpdatedAt(Instant.now());

        CustomOrder saved = customOrderRepository.save(customOrder);
        log.info("Tạo yêu cầu in custom thành công. ID đơn: {}", saved.getId());

        return customOrderMapper.toRequestResponseDTO(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomOrderDetailResponseDTO> getMyCustomOrders(User user) {
        return customOrderMapper.toDTOList(customOrderRepository.findByBuyerIdOrderByCreatedAtDesc(user.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomOrderDetailResponseDTO> getAllCustomOrders() {
        return customOrderMapper.toDTOList(customOrderRepository.findAllByOrderByCreatedAtDesc());
    }

    @Override
    @Transactional(readOnly = true)
    public CustomOrderDetailResponseDTO getCustomOrderDetail(UUID id, User currentUser) {
        CustomOrder order = customOrderRepository.findById(id)
                .orElseThrow(() -> new ApiException(CommonErrorCode.RESOURCE_NOT_FOUND,
                        "Không tìm thấy yêu cầu in ID: " + id));
        boolean isAdmin = currentUser != null && currentUser.getRole() == UserRole.ADMIN;
        boolean isOwner = currentUser != null && order.getBuyer() != null
                && order.getBuyer().getId().equals(currentUser.getId());
        if (!isAdmin && !isOwner) {
            throw new ApiException(CommonErrorCode.FORBIDDEN,
                    "Bạn không có quyền xem yêu cầu in này");
        }
        return customOrderMapper.toDTO(order);
    }

    @Override
    @Transactional(readOnly = true)
    public CustomOrderDetailResponseDTO getAdminCustomOrderDetail(UUID id) {
        CustomOrder order = customOrderRepository.findById(id)
                .orElseThrow(() -> new ApiException(CommonErrorCode.RESOURCE_NOT_FOUND,
                        "Không tìm thấy yêu cầu in ID: " + id));
        return customOrderMapper.toDTO(order);
    }

    @Override
    public CustomOrderDetailResponseDTO createCustomOrder(User user, CustomOrderCreateRequestDTO request) {
        String attachmentUrl;
        if (request.attachmentUrl() != null && !request.attachmentUrl().isBlank()) {
            attachmentUrl = request.attachmentUrl();
        } else if (request.fileId() != null) {
            FileAsset file = fileAssetRepository.findByIdAndDeletedFalse(request.fileId())
                    .orElseThrow(() -> new ApiException(VaultErrorCode.FILE_NOT_FOUND, "Không tìm thấy file thiết kế"));

            if (!file.getOwner().getId().equals(user.getId()) && user.getRole() != UserRole.ADMIN) {
                throw new ApiException(VaultErrorCode.FORBIDDEN_FILE_ACCESS);
            }
            attachmentUrl = "/api/vault/files/" + file.getId() + "/download";
        } else {
            throw new ApiException(CommonErrorCode.INVALID_INPUT, "Vui lòng đính kèm file 3D (attachmentUrl hoặc fileId)");
        }

        CustomOrder order = new CustomOrder();
        order.setBuyer(user);
        order.setAttachmentUrl(attachmentUrl);
        order.setRequirements(request.requirements());
        order.setQuantity(request.quantity());
        order.setShippingAddress(request.shippingAddress());
        order.setRulerModel(request.rulerModel());
        order.setCustomName(request.customName());
        order.setCustomStudentId(request.customStudentId());
        order.setColor(request.color());
        order.setFontStyle(request.fontStyle());
        // Đơn hàng in tùy chỉnh bắt buộc sử dụng thanh toán trực tuyến PayOS
        order.setPaymentMethod("PAYOS");
        order.setStatus("REQUESTED");
        order.setCreatedAt(Instant.now());
        order.setUpdatedAt(Instant.now());

        customOrderRepository.save(order);
        return customOrderMapper.toDTO(order);
    }

    @Override
    public void quoteCustomOrder(UUID id, CustomOrderQuoteRequestDTO request, User admin) {
        quotationService.quoteCustomOrder(id, request, admin);
    }

    @Override
    public void updateCustomOrderStatus(UUID id, CustomOrderStatusRequestDTO request, User user) {
        CustomOrder order = customOrderRepository.findLockedById(id)
                .orElseThrow(() -> new ApiException(CommonErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy yêu cầu in"));

        Payment p = customOrderMapper.findOrderPayment(order);

        String paymentMethod = order.getPaymentMethod() != null ? order.getPaymentMethod() : (p != null ? p.getGateway() : "PAYOS");
        boolean zeroAmountSettlement = order.resolveZeroAmountSettledAt() != null;
        String paymentStatus = zeroAmountSettlement ? "SUCCESS" : p != null ? p.getStatus() : "PENDING";

        String target = orderTransitionPolicy.normalizeTargetStatus(request.getEffectiveTarget());
        if ("ACCEPTED".equals(target)) {
            quotationService.validateAcceptRequestBeforeTransition(order, request, user);
            if (quotationService.isAlreadyConfirmedWithSamePrice(order, request)) {
                return;
            }
        }

        String resolvedStatus = orderTransitionPolicy.validateAndResolveNextStatus(
                OrderTransitionPolicy.OrderType.CUSTOM,
                order.getStatus(),
                request.getEffectiveTarget(),
                paymentMethod,
                paymentStatus,
                user,
                order.getBuyer().getId()
        );

        if ("ACCEPTED".equals(resolvedStatus)) {
            quotationService.confirmCustomQuote(order, request);
            if (order.getZeroAmountSettledAt() != null) {
                resolvedStatus = "PAID";
            }
        } else if ("CANCELLED".equals(resolvedStatus)) {
            applyCancellation(order, request, user);
        }

        Instant transitionAt = Instant.now();
        order.setStatus(resolvedStatus);
        order.setUpdatedAt(transitionAt);
        if ("COMPLETED".equals(resolvedStatus) && order.getCompletedAt() == null) {
            order.setCompletedAt(transitionAt);
        }
        customOrderRepository.save(order);

        if ("COMPLETED".equals(resolvedStatus) && orderRewardService != null) {
            orderRewardService.processCompletionReward(PaymentEvidenceService.SourceType.CUSTOM_ORDER, id);
        }

        sendStatusNotification(id, order, resolvedStatus, user);
    }

    @Override
    public RewardCompletionResponseDTO completeRewards(UUID customOrderId) {
        if (orderRewardService == null) {
            throw new ApiException(CommonErrorCode.INTERNAL_ERROR, "OrderRewardService chưa sẵn sàng");
        }
        var checkoutId = customOrderRepository.findCheckoutId(customOrderId);
        PaymentEvidenceService.SourceType targetType = PaymentEvidenceService.SourceType.CUSTOM_ORDER;
        UUID targetId = customOrderId;
        if (checkoutId.isPresent()) {
            CustomOrder child = customOrderRepository.findById(customOrderId)
                    .orElseThrow(() -> new ApiException(CommonErrorCode.RESOURCE_NOT_FOUND,
                            "Không tìm thấy yêu cầu in ID: " + customOrderId));
            if (!"COMPLETED".equals(child.getStatus())) {
                throw new ApiException(CommonErrorCode.BAD_REQUEST, "Yêu cầu in chưa ở trạng thái COMPLETED");
            }
            targetType = PaymentEvidenceService.SourceType.CHECKOUT_ORDER;
            targetId = checkoutId.get();
        }
        var reward = orderRewardService.processCompletionReward(targetType, targetId);
        if (reward.status() == OrderRewardStatus.PENDING_PAYMENT_EVIDENCE) {
            throw new ApiException(CommonErrorCode.BAD_REQUEST,
                    "Yêu cầu in chưa hoàn tất thanh toán hợp lệ để nhận điểm thưởng");
        }
        return reward.toLegacyResponse().withOrderId(customOrderId);
    }

    @Override
    @Transactional(readOnly = true)
    public CustomOrderFileDTO getCustomOrderFile(UUID id, User currentUser) {
        CustomOrder order = customOrderRepository.findById(id)
                .orElseThrow(() -> new ApiException(CommonErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy yêu cầu in tùy chỉnh."));

        if (currentUser.getRole() != UserRole.ADMIN && !order.getBuyer().getId().equals(currentUser.getId())) {
            throw new ApiException(CommonErrorCode.FORBIDDEN, "Bạn không có quyền tải tệp thiết kế của đơn hàng này.");
        }

        String url = order.getAttachmentUrl();
        if (url == null || url.isBlank() || "custom-model".equalsIgnoreCase(url.trim())) {
            throw new ApiException(CommonErrorCode.RESOURCE_NOT_FOUND, "Đơn hàng này không có tệp 3D đính kèm.");
        }

        try {
            URL fileUrl = new URI(url).toURL();
            HttpURLConnection conn = (HttpURLConnection) fileUrl.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(20000);
            if (conn.getResponseCode() >= 400) {
                throw new ApiException(CommonErrorCode.INTERNAL_ERROR,
                        "Không thể tải tệp từ kho lưu trữ đám mây (HTTP " + conn.getResponseCode() + ").");
            }
            byte[] bytes = conn.getInputStream().readAllBytes();

            String ext = detectExtension(url);
            String modelName = order.getRulerModel() != null && !order.getRulerModel().isBlank()
                    ? order.getRulerModel().replaceAll("[^a-zA-Z0-9_-]", "_")
                    : "model";
            String filename = "PrintHub3D_" + modelName + "_" + id.toString().substring(0, 8) + "." + ext;
            String contentType = resolveContentType(ext);

            return CustomOrderFileDTO.builder()
                    .data(bytes)
                    .filename(filename)
                    .contentType(contentType)
                    .build();
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Lỗi khi tải stream tệp từ Cloudinary: {}", url, e);
            throw new ApiException(CommonErrorCode.INTERNAL_ERROR, "Lỗi khi xử lý tải tệp: " + e.getMessage());
        }
    }

    private void applyCancellation(CustomOrder order, CustomOrderStatusRequestDTO request, User user) {
        boolean isAdminActor = user != null && user.getRole() == UserRole.ADMIN;
        String reason = request.getEffectiveCancelReason();
        if (isAdminActor) {
            if (reason == null || reason.length() < 5) {
                throw new ApiException(CommonErrorCode.INVALID_INPUT,
                        "Vui lòng nhập lý do hủy đơn (tối thiểu 5 ký tự).");
            }
        } else if (reason == null || reason.isBlank()) {
            reason = "Khách hàng tự hủy yêu cầu";
        }
        order.setCancelReason(reason);
        order.setCancelledByRole(isAdminActor ? "ADMIN" : "BUYER");
        if (user != null) {
            order.setCancelledById(user.getId());
        }
        order.setCancelledAt(Instant.now());
        if (order.getCheckoutOrder() == null && subscriptionBenefitService != null) {
            subscriptionBenefitService.releaseReservedBenefits(
                    SubscriptionBenefitService.UsageTargetType.CUSTOM_ORDER, order.getId());
        }
    }

    private void sendStatusNotification(UUID id, CustomOrder order, String resolvedStatus, User user) {
        if (notificationService == null || order.getBuyer() == null) {
            return;
        }
        boolean isAdminActor = user != null && user.getRole() == UserRole.ADMIN;
        if ("CANCELLED".equals(resolvedStatus) && isAdminActor) {
            String shortId = id.toString().length() >= 8 ? id.toString().substring(0, 8) : id.toString();
            notificationService.sendNotification(order.getBuyer(),
                    "Yêu cầu báo giá đã bị hủy",
                    "Yêu cầu báo giá #" + shortId + " đã bị xưởng hủy. Lý do: " + order.getCancelReason(),
                    "CUSTOM_ORDER",
                    "/quotations");
        } else {
            notificationService.sendNotification(order.getBuyer(),
                    "Cập nhật tiến trình in",
                    "Yêu cầu in 3D " + id + " đã chuyển sang trạng thái: " + resolvedStatus,
                    "CUSTOM_ORDER",
                    "/quotations");
        }
    }

    private String detectExtension(String url) {
        String lowerUrl = url.toLowerCase();
        if (lowerUrl.contains(".obj")) return "obj";
        if (lowerUrl.contains(".step")) return "step";
        if (lowerUrl.contains(".stp")) return "stp";
        if (lowerUrl.contains(".3mf")) return "3mf";
        if (lowerUrl.contains(".glb")) return "glb";
        if (lowerUrl.contains(".gltf")) return "gltf";
        if (lowerUrl.contains(".png")) return "png";
        if (lowerUrl.contains(".jpg") || lowerUrl.contains(".jpeg")) return "jpg";
        return "stl";
    }

    private String resolveContentType(String ext) {
        return switch (ext) {
            case "stl" -> "model/stl";
            case "glb" -> "model/gltf-binary";
            case "gltf" -> "model/gltf+json";
            case "png" -> "image/png";
            case "jpg" -> "image/jpeg";
            default -> "application/octet-stream";
        };
    }
}
