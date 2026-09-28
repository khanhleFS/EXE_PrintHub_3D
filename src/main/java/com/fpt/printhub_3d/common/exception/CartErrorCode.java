package com.fpt.printhub_3d.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CartErrorCode implements ErrorCode {

    PRODUCT_NOT_FOUND(25001, "Sản phẩm không tồn tại hoặc đã bị xóa", HttpStatus.NOT_FOUND, "cart.product_not_found"),
    PRODUCT_INACTIVE(25002, "Sản phẩm hiện không còn hoạt động hoặc đã ngừng bán", HttpStatus.BAD_REQUEST, "cart.product_inactive"),
    INVALID_QUANTITY(25003, "Số lượng sản phẩm không hợp lệ (1-999)", HttpStatus.BAD_REQUEST, "cart.invalid_quantity"),
    DUPLICATE_PRODUCT(25004, "Sản phẩm bị trùng lặp trong giỏ hàng", HttpStatus.BAD_REQUEST, "cart.duplicate_product"),
    CART_NOT_FOUND(25005, "Không tìm thấy thông tin giỏ hàng", HttpStatus.NOT_FOUND, "cart.not_found");

    private final int code;
    private final String message;
    private final HttpStatus status;
    private final String errorKey;

    @Override
    public String getDomain() {
        return "CART";
    }
}
