package com.fpt.printhub_3d.service;

import com.fpt.printhub_3d.dto.checkout.AdminCheckoutOrderStatusRequestDTO;
import com.fpt.printhub_3d.dto.checkout.CheckoutOrderConfirmRequestDTO;
import com.fpt.printhub_3d.dto.checkout.CheckoutOrderCreateRequestDTO;
import com.fpt.printhub_3d.dto.checkout.CheckoutOrderResponseDTO;
import com.fpt.printhub_3d.entity.User;

import java.util.List;
import java.util.UUID;

public interface CheckoutOrderService {

    CheckoutOrderResponseDTO createCheckoutOrder(User user, CheckoutOrderCreateRequestDTO request);

    CheckoutOrderResponseDTO getCheckoutOrder(User user, UUID id);

    List<CheckoutOrderResponseDTO> getMyCheckoutOrders(User user);

    CheckoutOrderResponseDTO confirmCheckoutOrder(User user, UUID id, CheckoutOrderConfirmRequestDTO request);

    List<CheckoutOrderResponseDTO> getAdminCheckoutOrders(String orderType, String status, String query);

    CheckoutOrderResponseDTO getAdminCheckoutOrder(UUID id);

    CheckoutOrderResponseDTO batchQuoteCheckoutOrder(User admin, UUID id, com.fpt.printhub_3d.dto.checkout.CheckoutOrderBatchQuoteRequestDTO request);

    CheckoutOrderResponseDTO updateAdminCheckoutOrderStatus(User admin, UUID id, AdminCheckoutOrderStatusRequestDTO request);
}
