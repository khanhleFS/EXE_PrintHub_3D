package com.fpt.printhub_3d.service;

import com.fpt.printhub_3d.dto.cart.CartItemResponseDTO;
import com.fpt.printhub_3d.dto.cart.CartUpdateRequestDTO;
import com.fpt.printhub_3d.entity.User;

import java.util.List;

public interface CartService {
    List<CartItemResponseDTO> getCart(User user);
    List<CartItemResponseDTO> updateCart(User user, CartUpdateRequestDTO request);
}
