package com.fpt.printhub_3d.service;

import com.fpt.printhub_3d.dto.cart.CartItemResponseDTO;
import com.fpt.printhub_3d.dto.cart.CartItemUpdateRequestDTO;
import com.fpt.printhub_3d.dto.cart.CartUpdateRequestDTO;
import com.fpt.printhub_3d.entity.User;

import java.util.List;
import java.util.UUID;

public interface CartService {
    List<CartItemResponseDTO> getCart(User user);
    List<CartItemResponseDTO> updateCart(User user, CartUpdateRequestDTO request);
    CartItemResponseDTO updateCartItem(User user, UUID clientLineId, CartItemUpdateRequestDTO request);
    void deleteCartItem(User user, UUID clientLineId);
    List<CartItemResponseDTO> mergeGuestCart(User user, com.fpt.printhub_3d.dto.cart.CartMergeGuestRequestDTO request);
}
