package com.fpt.printhub_3d.service.impl;

import com.fpt.printhub_3d.common.exception.ApiException;
import com.fpt.printhub_3d.common.exception.CartErrorCode;
import com.fpt.printhub_3d.dto.cart.CartItemRequestDTO;
import com.fpt.printhub_3d.dto.cart.CartItemResponseDTO;
import com.fpt.printhub_3d.dto.cart.CartItemUpdateRequestDTO;
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

    private CartItemResponseDTO toDTO(CartItem item) {
        com.fpt.printhub_3d.dto.marketplace.ProductResponseDTO productDTO = null;
        if (item.getProduct() != null) {
            try {
                productDTO = productService.getProductById(item.getProduct().getId());
            } catch (Exception e) {
                // ignore product resolution error
            }
        }
        return CartItemResponseDTO.builder()
                .id(item.getId())
                .clientLineId(item.getClientLineId())
                .revision(item.getRevision() != null ? item.getRevision() : 1L)
                .quantity(item.getQuantity())
                .product(productDTO)
                .customDesign(item.getCustomDesign())
                .customName(item.getCustomName())
                .customPrice(item.getCustomPrice())
                .thumbnailUrl(item.getThumbnailUrl())
                .build();
    }

    @Override
    public List<CartItemResponseDTO> getCart(User user) {
        Cart cart = getOrCreateCart(user);
        List<CartItem> items = cartItemRepository.findByCartIdOrderByIdAsc(cart.getId());
        boolean hasBackfill = false;
        for (CartItem item : items) {
            if (item.getClientLineId() == null) {
                item.setClientLineId(UUID.randomUUID());
                item.setRevision(1L);
                cartItemRepository.save(item);
                hasBackfill = true;
            }
        }
        if (hasBackfill) {
            items = cartItemRepository.findByCartIdOrderByIdAsc(cart.getId());
        }
        return items.stream().map(this::toDTO).toList();
    }

    @Override
    public List<CartItemResponseDTO> updateCart(User user, CartUpdateRequestDTO request) {
        Cart cart = getOrCreateCart(user);

        List<CartItemRequestDTO> items = request.items();
        if (items != null) {
            Set<UUID> seenProductIds = new HashSet<>();
            for (CartItemRequestDTO line : items) {
                boolean isCustom = line.customDesign() != null && !line.customDesign().isBlank();
                if (!isCustom) {
                    if (line.productId() == null) {
                        throw new ApiException(CartErrorCode.PRODUCT_NOT_FOUND);
                    }
                    if (!seenProductIds.add(line.productId())) {
                        throw new ApiException(CartErrorCode.DUPLICATE_PRODUCT);
                    }
                    Product product = productRepository.findById(line.productId())
                            .orElseThrow(() -> new ApiException(CartErrorCode.PRODUCT_NOT_FOUND));

                    if (!"ACTIVE".equals(product.getStatus())) {
                        throw new ApiException(CartErrorCode.PRODUCT_INACTIVE);
                    }
                }
            }

            cartItemRepository.deleteByCartId(cart.getId());

            for (CartItemRequestDTO line : items) {
                boolean isCustom = line.customDesign() != null && !line.customDesign().isBlank();
                CartItem item = new CartItem();
                item.setCart(cart);
                item.setQuantity(line.quantity());
                item.setClientLineId(line.clientLineId() != null ? line.clientLineId() : UUID.randomUUID());
                item.setRevision(line.revision() != null ? line.revision() : 1L);

                if (isCustom) {
                    item.setCustomDesign(line.customDesign());
                    item.setCustomName(line.customName());
                    // Giá custom chỉ do xưởng phát hành sau khi kiểm tra file 3D; không lưu giá từ client
                    item.setCustomPrice(null);
                    item.setThumbnailUrl(line.thumbnailUrl());
                    if (line.productId() != null) {
                        productRepository.findById(line.productId()).ifPresent(item::setProduct);
                    }
                } else {
                    Product product = productRepository.getReferenceById(line.productId());
                    item.setProduct(product);
                }
                cartItemRepository.save(item);
            }
        } else {
            cartItemRepository.deleteByCartId(cart.getId());
        }

        cart.setUpdatedAt(Instant.now());
        cartRepository.save(cart);

        return getCart(user);
    }

    @Override
    public CartItemResponseDTO updateCartItem(User user, UUID clientLineId, CartItemUpdateRequestDTO request) {
        Cart cart = getOrCreateCart(user);
        CartItem item = cartItemRepository.findByCartIdAndClientLineId(cart.getId(), clientLineId)
                .orElseThrow(() -> new ApiException(CartErrorCode.LINE_NOT_FOUND));

        if (request.revision() != null && item.getRevision() != null && item.getRevision() > request.revision()) {
            throw new ApiException(CartErrorCode.VERSION_CONFLICT);
        }

        if (request.quantity() != null) {
            if (request.quantity() < 1 || request.quantity() > 999) {
                throw new ApiException(CartErrorCode.INVALID_QUANTITY);
            }
            item.setQuantity(request.quantity());
        }

        if (request.customDesign() != null) {
            item.setCustomDesign(request.customDesign());
        }
        if (request.customName() != null) {
            item.setCustomName(request.customName());
        }
        if (request.customPrice() != null) {
            item.setCustomPrice(request.customPrice());
        }
        if (request.thumbnailUrl() != null) {
            item.setThumbnailUrl(request.thumbnailUrl());
        }

        long currentRev = item.getRevision() != null ? item.getRevision() : 1L;
        item.setRevision(currentRev + 1L);

        cartItemRepository.save(item);
        cart.setUpdatedAt(Instant.now());
        cartRepository.save(cart);

        return toDTO(item);
    }

    @Override
    public void deleteCartItem(User user, UUID clientLineId) {
        Cart cart = getOrCreateCart(user);
        var optionalItem = cartItemRepository.findByCartIdAndClientLineId(cart.getId(), clientLineId);
        if (optionalItem.isEmpty()) {
            // Idempotent: Nếu dòng giỏ hàng đã bị xóa trước đó (ví dụ do checkout order đã xóa),
            // bỏ qua an toàn thay vì ném lỗi 404/25006
            return;
        }

        cartItemRepository.delete(optionalItem.get());
        cart.setUpdatedAt(Instant.now());
        cartRepository.save(cart);
    }
}
