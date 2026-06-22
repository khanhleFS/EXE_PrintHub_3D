package com.fpt.printhub_3d.service;

import com.fpt.printhub_3d.dto.custom_prints.CustomOrderResponseDTO;
import com.fpt.printhub_3d.entity.User;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface CustomOrderService {
    /**
     * Khởi tạo yêu cầu gia công đặt in Custom đặc thù.
     *
     * @param makerId ID của Maker
     * @param requirements Mô tả yêu cầu gia công
     * @param file Tệp thiết kế hình học định dạng .STL
     * @param buyer Đối tượng người mua hiện tại (Customer)
     * @return Thông tin yêu cầu in custom vừa khởi tạo
     */
    CustomOrderResponseDTO createRequest(UUID makerId, String requirements, MultipartFile file, User buyer);
}
