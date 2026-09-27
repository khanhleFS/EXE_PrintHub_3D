package com.fpt.printhub_3d.service;

import com.fpt.printhub_3d.dto.custom_prints.*;
import com.fpt.printhub_3d.entity.User;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface CustomOrderService {
    CustomOrderResponseDTO createRequest(UUID makerId, String requirements, MultipartFile file, User buyer);
    List<CustomOrderDetailResponseDTO> getMyCustomOrders(User user);
    List<CustomOrderDetailResponseDTO> getAllCustomOrders();
    CustomOrderDetailResponseDTO createCustomOrder(User user, CustomOrderCreateRequestDTO request);
    void quoteCustomOrder(UUID id, CustomOrderQuoteRequestDTO request, User maker);
    void updateCustomOrderStatus(UUID id, CustomOrderStatusRequestDTO request, User user);
}
