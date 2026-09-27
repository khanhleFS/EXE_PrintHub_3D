package com.fpt.printhub_3d.service.impl;

import com.fpt.printhub_3d.common.exception.ApiException;
import com.fpt.printhub_3d.common.exception.CartErrorCode;
import com.fpt.printhub_3d.dto.cart.CartItemRequestDTO;
import com.fpt.printhub_3d.dto.cart.CartItemResponseDTO;
import com.fpt.printhub_3d.dto.cart.CartUpdateRequestDTO;
import com.fpt.printhub_3d.entity.Cart;
import com.fpt.printhub_3d.entity.CartItem;
import com.fpt.printhub_3d.entity.Product;
import com.fpt.printhub_3d.entity.User;
import com.fpt.printhub_3d.repository.CartItemRepository;
import com.fpt.printhub_3d.repository.CartRepository;
import com.fpt.printhub_3d.repository.ProductRepository;
import com.fpt.printhub_3d.service.CartService;
import com.fpt.printhub_3d.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final ProductService productService;

    private Cart getOrCreateCart(User user) {
        return cartRepository.findByUserId(user.getId())
                .orElseGet(() -> {
                    Cart cart = new Cart();
                    cart.setUser(user);
                    cart.setCreatedAt(Instant.now());
                    cart.setUpdatedAt(Instant.now());
                    return cartRepository.save(cart);
                });
    }

    @Override
    @Transactional(readOnly = true)
    public List<CartItemResponseDTO> getCart(User user) {
        Cart cart = getOrCreateCart(user);
        List<CartItem> items = cartItemRepository.findByCartIdOrderByIdAsc(cart.getId());
        return items.stream()
                .map(item -> CartItemResponseDTO.builder()
                        .id(item.getId())
                        .quantity(item.getQuantity())
                        .product(productService.getProductById(item.getProduct().getId()))
                        .build())
                .toList();
    }

    @Override
    public List<CartItemResponseDTO> updateCart(User user, CartUpdateRequestDTO request) {
        Cart cart = getOrCreateCart(user);

        List<CartItemRequestDTO> items = request.items();
        if (items != null) {
            Set<UUID> seenProductIds = new HashSet<>();
            for (CartItemRequestDTO line : items) {
                if (!seenProductIds.add(line.productId())) {
                    throw new ApiException(CartErrorCode.DUPLICATE_PRODUCT);
                }
                Product product = productRepository.findById(line.productId())
                        .orElseThrow(() -> new ApiException(CartErrorCode.PRODUCT_NOT_FOUND));

                if (!"ACTIVE".equals(product.getStatus())) {
                    throw new ApiException(CartErrorCode.PRODUCT_INACTIVE);
                }
            }

            cartItemRepository.deleteByCartId(cart.getId());

            for (CartItemRequestDTO line : items) {
                Product product = productRepository.getReferenceById(line.productId());
                CartItem item = new CartItem();
                item.setCart(cart);
                item.setProduct(product);
                item.setQuantity(line.quantity());
                cartItemRepository.save(item);
            }
        } else {
            cartItemRepository.deleteByCartId(cart.getId());
        }

        cart.setUpdatedAt(Instant.now());
        cartRepository.save(cart);

        return getCart(user);
    }
}
