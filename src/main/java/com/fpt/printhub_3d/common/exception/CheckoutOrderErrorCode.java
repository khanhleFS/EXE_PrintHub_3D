package com.fpt.printhub_3d.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CheckoutOrderErrorCode implements ErrorCode {

    CHECKOUT_ORDER_NOT_FOUND(28001, "Đơn thanh toán không tồn tại hoặc đã bị hủy", HttpStatus.NOT_FOUND, "checkout.not_found"),
    EMPTY_SELECTION(28002, "Vui lòng chọn ít nhất 1 sản phẩm từ giỏ hàng để tạo đơn", HttpStatus.BAD_REQUEST, "checkout.empty_selection"),
    SELLER_MISMATCH_FOR_COMMON_SHIPPING(28003, "Giao chung chỉ áp dụng khi các sản phẩm thuộc cùng một xưởng/người bán. Vui lòng chọn Giao riêng", HttpStatus.BAD_REQUEST, "checkout.seller_mismatch_common_shipping"),
    CUSTOM_NOT_ALL_QUOTED(28004, "Đơn hàng vẫn còn sản phẩm in 3D đang chờ xưởng thẩm định và báo giá", HttpStatus.BAD_REQUEST, "checkout.custom_not_all_quoted"),
    MARKETPLACE_OUT_OF_STOCK(28005, "Sản phẩm bán sẵn trong đơn không còn đủ tồn kho. Vui lòng kiểm tra lại", HttpStatus.BAD_REQUEST, "checkout.marketplace_out_of_stock"),
    MARKETPLACE_PRICE_CHANGED(28006, "Giá sản phẩm bán sẵn đã thay đổi kể từ khi tạo đơn. Vui lòng xem và xác nhận lại tổng tiền", HttpStatus.CONFLICT, "checkout.marketplace_price_changed"),
    CONFIRMATION_VERSION_CONFLICT(28007, "Phiên bản xác nhận đơn hàng không khớp. Vui lòng làm mới trang để cập nhật tổng tiền mới nhất", HttpStatus.CONFLICT, "checkout.version_conflict"),
    INVALID_PAYMENT_STATE(28008, "Đơn hàng chưa ở trạng thái sẵn sàng để tạo giao dịch thanh toán", HttpStatus.BAD_REQUEST, "checkout.invalid_payment_state"),
    CANNOT_CANCEL_CONFIRMED_ORDER(28009, "Không thể hủy đơn hàng đã bước vào giai đoạn sản xuất hoặc giao hàng", HttpStatus.BAD_REQUEST, "checkout.cannot_cancel_confirmed");

    private final int code;
    private final String message;
    private final HttpStatus status;
    private final String errorKey;

    @Override
    public String getDomain() {
        return "CHECKOUT_ORDER";
    }
}
