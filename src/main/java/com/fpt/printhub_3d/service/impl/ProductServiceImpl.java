package com.fpt.printhub_3d.service.impl;

import com.fpt.printhub_3d.common.exception.ApiException;
import com.fpt.printhub_3d.common.exception.CommonErrorCode;
import com.fpt.printhub_3d.dto.marketplace.ProductFilterDTO;
import com.fpt.printhub_3d.dto.marketplace.ProductResponseDTO;
import com.fpt.printhub_3d.entity.Product;
import com.fpt.printhub_3d.entity.ProductImage;
import com.fpt.printhub_3d.repository.ProductImageRepository;
import com.fpt.printhub_3d.repository.ProductRepository;
import com.fpt.printhub_3d.repository.specification.ProductSpecification;
import com.fpt.printhub_3d.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import com.fpt.printhub_3d.dto.marketplace.CreateProductRequestDTO;
import com.fpt.printhub_3d.entity.Category;
import com.fpt.printhub_3d.entity.User;
import com.fpt.printhub_3d.repository.CategoryRepository;
import com.fpt.printhub_3d.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("price", "createdAt", "title");
    private static final int DEFAULT_PAGE_SIZE = 12;
    private static final int MAX_PAGE_SIZE = 100;

    @Override
    @Transactional
    public ProductResponseDTO createProduct(CreateProductRequestDTO request, UUID sellerId) {
        log.info("Tạo sản phẩm mới trên marketplace: title={}, sellerId={}", request.title(), sellerId);

        User seller = userRepository.findById(sellerId)
                .orElseThrow(() -> new ApiException(CommonErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy người bán"));

        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ApiException(CommonErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy danh mục"));

        Product product = new Product();
        product.setSeller(seller);
        product.setCategory(category);
        product.setTitle(request.title());
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setStock(request.stock());
        product.setType(request.type());
        product.setStatus("ACTIVE");
        product.setCreatedAt(Instant.now());
        product.setUpdatedAt(Instant.now());

        Product savedProduct = productRepository.save(product);

        List<ProductImage> savedImages = new ArrayList<>();
        if (request.imageUrls() != null && !request.imageUrls().isEmpty()) {
            for (int i = 0; i < request.imageUrls().size(); i++) {
                ProductImage image = new ProductImage();
                image.setProduct(savedProduct);
                image.setImageUrl(request.imageUrls().get(i));
                image.setIsPrimary(i == 0);
                savedImages.add(productImageRepository.save(image));
            }
        }

        return mapToResponseDTO(savedProduct, savedImages);
    }

    @Override
    public Page<ProductResponseDTO> getProducts(ProductFilterDTO filter) {
        log.info("Lấy danh sách sản phẩm marketplace với bộ lọc: keyword={}, categoryId={}, type={}, minPrice={}, maxPrice={}",
                filter.keyword(), filter.categoryId(), filter.type(), filter.minPrice(), filter.maxPrice());

        Pageable pageable = buildPageable(filter);
        Specification<Product> spec = ProductSpecification.withFilter(filter);

        Page<Product> productPage = productRepository.findAll(spec, pageable);

        List<Product> products = productPage.getContent();
        Map<UUID, List<ProductImage>> imagesByProduct = fetchImagesForProducts(products);

        log.info("Tìm thấy {} sản phẩm (trang {}/{})",
                productPage.getTotalElements(), productPage.getNumber() + 1, productPage.getTotalPages());

        return productPage.map(product -> mapToResponseDTO(product, imagesByProduct.getOrDefault(product.getId(), Collections.emptyList())));
    }

    @Override
    public ProductResponseDTO getProductById(UUID id) {
        log.info("Lấy chi tiết sản phẩm với ID: {}", id);
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ApiException(CommonErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy sản phẩm"));

        List<ProductImage> images = productImageRepository.findByProductIn(List.of(product));

        return mapToResponseDTO(product, images);
    }

    // ──────────────────────────── Private Helpers ────────────────────────────

    private Pageable buildPageable(ProductFilterDTO filter) {
        int page = (filter.page() != null && filter.page() >= 0) ? filter.page() : 0;
        int size = (filter.size() != null && filter.size() >= 1)
                ? Math.min(filter.size(), MAX_PAGE_SIZE)
                : DEFAULT_PAGE_SIZE;

        String sortBy = (filter.sortBy() != null && ALLOWED_SORT_FIELDS.contains(filter.sortBy()))
                ? filter.sortBy()
                : "createdAt";

        Sort.Direction direction = "asc".equalsIgnoreCase(filter.sortDirection())
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        return PageRequest.of(page, size, Sort.by(direction, sortBy));
    }

    private Map<UUID, List<ProductImage>> fetchImagesForProducts(List<Product> products) {
        if (products.isEmpty()) {
            return Collections.emptyMap();
        }
        List<ProductImage> allImages = productImageRepository.findByProductIn(products);
        return allImages.stream()
                .collect(Collectors.groupingBy(img -> img.getProduct().getId()));
    }

    private ProductResponseDTO mapToResponseDTO(Product product, List<ProductImage> images) {
        List<String> imageUrls = images.stream()
                .map(ProductImage::getImageUrl)
                .collect(Collectors.toList());

        String primaryImageUrl = images.stream()
                .filter(ProductImage::getIsPrimary)
                .map(ProductImage::getImageUrl)
                .findFirst()
                .orElse(imageUrls.isEmpty() ? null : imageUrls.get(0));

        return ProductResponseDTO.builder()
                .id(product.getId())
                .sellerId(product.getSeller().getId())
                .sellerName(product.getSeller().getFullName())
                .categoryId(product.getCategory().getCategoryId())
                .categoryName(product.getCategory().getCategoryName())
                .title(product.getTitle())
                .description(product.getDescription())
                .price(product.getPrice())
                .stock(product.getStock())
                .type(product.getType())
                .status(product.getStatus())
                .primaryImageUrl(primaryImageUrl)
                .imageUrls(imageUrls)
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }
}
