package com.fpt.printhub_3d.service;

import com.fpt.printhub_3d.common.exception.ApiException;
import com.fpt.printhub_3d.common.exception.CommonErrorCode;
import com.fpt.printhub_3d.common.exception.CustomPrintErrorCode;
import com.fpt.printhub_3d.common.exception.OrderErrorCode;
import com.fpt.printhub_3d.dto.order.BlockedActionDTO;
import com.fpt.printhub_3d.entity.Enumeration.UserRole;
import com.fpt.printhub_3d.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class OrderTransitionPolicy {

    public enum OrderType {
        MARKETPLACE,
        CUSTOM
    }

    public record TransitionEvaluation(
            List<String> allowedActions,
            List<BlockedActionDTO> blockedActions
    ) {}

    /**
     * Chuẩn hóa action hoặc status đầu vào sang canonical status.
     */
    public String normalizeTargetStatus(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String trimmed = input.trim().toUpperCase();
        return switch (trimmed) {
            case "START_PREPARATION", "PREPARE", "PREPARING" -> "PREPARING";
            case "START_PRINTING", "PRINT", "PRINTING" -> "PRINTING";
            case "START_SHIPPING", "SHIP", "SHIPPING" -> "SHIPPING";
            case "COMPLETE_DELIVERY", "COMPLETE", "COMPLETED" -> "COMPLETED";
            case "CANCEL", "CANCELLED" -> "CANCELLED";
            case "ACCEPT", "ACCEPTED" -> "ACCEPTED";
            case "QUOTE", "QUOTED" -> "QUOTED";
            case "EXPIRE", "EXPIRED" -> "EXPIRED";
            default -> trimmed;
        };
    }

    /**
     * Kiểm tra guard và validate tính hợp lệ của việc chuyển trạng thái.
     */
    public String validateAndResolveNextStatus(
            OrderType orderType,
            String currentStatus,
            String targetInput,
            String paymentMethod,
            String paymentStatus,
            User currentUser,
            UUID ownerId
    ) {
        String targetStatus = normalizeTargetStatus(targetInput);
        if (targetStatus == null) {
            throw new ApiException(CommonErrorCode.INVALID_INPUT, "Hành động hoặc trạng thái đích không được để trống");
        }

        String cur = currentStatus != null ? currentStatus.toUpperCase() : "PENDING";
        boolean isAdmin = currentUser != null && currentUser.getRole() == UserRole.ADMIN;
        boolean isOwner = currentUser != null && ownerId != null && currentUser.getId().equals(ownerId);

        // 1. Phân quyền hủy đơn (CANCELLED)
        if ("CANCELLED".equals(targetStatus)) {
            if (!isAdmin && !isOwner) {
                throw new ApiException(CommonErrorCode.FORBIDDEN, "Bạn không có quyền hủy đơn hàng này");
            }
            if (orderType == OrderType.MARKETPLACE) {
                if (!"PENDING".equals(cur)) {
                    throw new ApiException(OrderErrorCode.ORDER_ALREADY_CANCELLED,
                            "Chỉ được hủy đơn Marketplace khi đơn ở trạng thái PENDING");
                }
            } else {
                if (!"REQUESTED".equals(cur) && !"QUOTED".equals(cur)) {
                    throw new ApiException(CommonErrorCode.INVALID_INPUT,
                            "Chỉ có thể hủy yêu cầu in 3D khi chưa bước vào sản xuất");
                }
            }
            return targetStatus;
        }

        // 2. Chấp nhận báo giá Custom (ACCEPTED)
        if ("ACCEPTED".equals(targetStatus)) {
            if (orderType != OrderType.CUSTOM) {
                throw new ApiException(CommonErrorCode.INVALID_INPUT, "Chỉ đơn custom mới có bước ACCEPTED");
            }
            if (!isOwner) {
                throw new ApiException(CommonErrorCode.FORBIDDEN, "Chỉ người đặt mới có quyền duyệt báo giá");
            }
            if (!"QUOTED".equals(cur)) {
                throw new ApiException(CommonErrorCode.INVALID_INPUT, "Yêu cầu in chưa có báo giá để chấp thuận");
            }
            return targetStatus;
        }

        // 3. Các bước gia công / giao hàng / hoàn thành đều do ADMIN thực hiện
        if (!isAdmin) {
            throw new ApiException(CommonErrorCode.FORBIDDEN, "Chỉ quản trị viên mới có thể chuyển tiến trình đơn");
        }

        // 4. Validate cạnh chuyển trạng thái
        boolean isValidTransition;
        if (orderType == OrderType.MARKETPLACE) {
            isValidTransition = switch (cur) {
                case "PENDING", "PAID" -> "PREPARING".equals(targetStatus) || "PRINTING".equals(targetStatus);
                case "PREPARING" -> "SHIPPING".equals(targetStatus) || "PRINTING".equals(targetStatus);
                case "PRINTING" -> "SHIPPING".equals(targetStatus);
                case "SHIPPING" -> "COMPLETED".equals(targetStatus);
                default -> false;
            };
            if (!isValidTransition) {
                throw new ApiException(OrderErrorCode.INVALID_ORDER_TRANSITION,
                        "Chuyển trạng thái Marketplace không hợp lệ từ " + cur + " sang " + targetStatus);
            }
        } else {
            isValidTransition = switch (cur) {
                case "ACCEPTED", "PAID" -> "PREPARING".equals(targetStatus) || "PRINTING".equals(targetStatus);
                case "PREPARING" -> "PRINTING".equals(targetStatus);
                case "PRINTING" -> "SHIPPING".equals(targetStatus);
                case "SHIPPING" -> "COMPLETED".equals(targetStatus);
                default -> false;
            };
            if (!isValidTransition) {
                throw new ApiException(CustomPrintErrorCode.INVALID_ORDER_TRANSITION,
                        "Chuyển trạng thái Custom Order không hợp lệ từ " + cur + " sang " + targetStatus);
            }
        }

        // 5. CHỐT CHẶN THANH TOÁN (PAYMENT GUARDS)
        // Khi chuyển sang PREPARING hoặc PRINTING:
        if ("PREPARING".equals(targetStatus) || "PRINTING".equals(targetStatus)) {
            boolean isPayos = paymentMethod == null || "PAYOS".equalsIgnoreCase(paymentMethod);
            if (isPayos) {
                boolean isPaid = paymentStatus != null && "SUCCESS".equalsIgnoreCase(paymentStatus);
                if (!isPaid) {
                    if (orderType == OrderType.MARKETPLACE) {
                        throw new ApiException(OrderErrorCode.PAYMENT_REQUIRED,
                                "Đơn hàng PayOS chưa thanh toán thành công, không thể đưa vào xử lý sản xuất");
                    } else {
                        throw new ApiException(CustomPrintErrorCode.PAYMENT_REQUIRED,
                                "Yêu cầu in 3D PayOS chưa thanh toán thành công, không thể đưa vào xử lý sản xuất");
                    }
                }
            }
        }

        return targetStatus;
    }

    /**
     * Tính toán danh sách hành động hợp lệ (allowedActions) và bị chặn (blockedActions).
     */
    public TransitionEvaluation evaluateActions(
            OrderType orderType,
            String currentStatus,
            String paymentMethod,
            String paymentStatus,
            boolean isAdmin,
            boolean isOwner
    ) {
        List<String> allowed = new ArrayList<>();
        List<BlockedActionDTO> blocked = new ArrayList<>();

        String cur = currentStatus != null ? currentStatus.toUpperCase() : "PENDING";
        boolean isPayos = paymentMethod == null || "PAYOS".equalsIgnoreCase(paymentMethod);
        boolean isPaid = paymentStatus != null && "SUCCESS".equalsIgnoreCase(paymentStatus);

        if (orderType == OrderType.MARKETPLACE) {
            if (isAdmin) {
                switch (cur) {
                    case "PENDING" -> {
                        allowed.add("CANCEL");
                        if (isPayos && !isPaid) {
                            blocked.add(BlockedActionDTO.builder()
                                    .action("START_PREPARATION")
                                    .reasonCode("PAYMENT_REQUIRED")
                                    .message("Đơn hàng PayOS chưa thanh toán thành công")
                                    .build());
                        } else {
                            allowed.add("START_PREPARATION");
                        }
                    }
                    case "PAID" -> allowed.add("START_PREPARATION");
                    case "PREPARING" -> allowed.add("START_SHIPPING");
                    case "PRINTING" -> allowed.add("START_SHIPPING");
                    case "SHIPPING" -> allowed.add("COMPLETE_DELIVERY");
                    default -> {}
                }
            } else if (isOwner) {
                if ("PENDING".equals(cur)) {
                    allowed.add("CANCEL");
                }
            }
        } else {
            // CUSTOM ORDER
            if (isAdmin) {
                switch (cur) {
                    case "REQUESTED" -> {
                        allowed.add("QUOTE");
                        allowed.add("CANCEL");
                    }
                    case "QUOTED" -> allowed.add("CANCEL");
                    case "ACCEPTED" -> {
                        if (isPayos && !isPaid) {
                            blocked.add(BlockedActionDTO.builder()
                                    .action("START_PREPARATION")
                                    .reasonCode("PAYMENT_REQUIRED")
                                    .message("Yêu cầu in 3D PayOS chưa thanh toán thành công")
                                    .build());
                        } else {
                            allowed.add("START_PREPARATION");
                        }
                    }
                    case "PAID" -> allowed.add("START_PREPARATION");
                    case "PREPARING" -> allowed.add("START_PRINTING");
                    case "PRINTING" -> allowed.add("START_SHIPPING");
                    case "SHIPPING" -> allowed.add("COMPLETE_DELIVERY");
                    default -> {}
                }
            } else if (isOwner) {
                switch (cur) {
                    case "REQUESTED" -> allowed.add("CANCEL");
                    case "QUOTED" -> {
                        allowed.add("ACCEPT");
                        allowed.add("CANCEL");
                    }
                    default -> {}
                }
            }
        }

        return new TransitionEvaluation(allowed, blocked);
    }
}
