package com.fpt.printhub_3d.service;

import com.fpt.printhub_3d.dto.order.OrderCreateRequestDTO;
import com.fpt.printhub_3d.dto.order.OrderResponseDTO;
import com.fpt.printhub_3d.entity.User;

import java.util.List;

public interface OrderService {
    /**
     * Khởi tạo đơn hàng mua sản phẩm bán sẵn dựa trên giỏ hàng hoặc danh sách mặt hàng đặt mua.
     * Nếu có nhiều sản phẩm từ nhiều Maker (seller) khác nhau, hệ thống sẽ tự động tách thành nhiều đơn hàng tương ứng.
     * Đồng thời trừ số lượng tồn kho (stock) của từng sản phẩm.
     *
     * @param request thông tin nhận hàng và danh sách sản phẩm cần mua
     * @param buyer   người mua (Customer)
     * @return danh sách các đơn hàng đã được tạo thành công
     */
    List<OrderResponseDTO> createOrders(OrderCreateRequestDTO request, User buyer);
}
