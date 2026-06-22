package com.fpt.printhub_3d.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum OrderErrorCode implements ErrorCode {

    PRODUCT_NOT_FOUND(20001, "Sản phẩm đặt mua không tồn tại hoặc đã bị xóa", HttpStatus.NOT_FOUND, "order.product_not_found"),
    OUT_OF_STOCK(20002, "Sản phẩm không đủ số lượng tồn kho", HttpStatus.BAD_REQUEST, "order.out_of_stock"),
    SELLER_INACTIVE(20003, "Cửa hàng/Maker của sản phẩm hiện không hoạt động", HttpStatus.BAD_REQUEST, "order.seller_inactive"),
    INVALID_ORDER_ITEMS(20004, "Danh sách sản phẩm mua hàng trống hoặc không hợp lệ", HttpStatus.BAD_REQUEST, "order.invalid_items");

    private final int code;
    private final String message;
    private final HttpStatus status;
    private final String errorKey;

    @Override
    public String getDomain() {
        return "ORDER";
    }
}
