package com.fpt.printhub_3d.service.impl;

import com.fpt.printhub_3d.dto.custom_prints.CustomPrintServiceFilterDTO;
import com.fpt.printhub_3d.dto.custom_prints.CustomPrintServiceRequestDTO;
import com.fpt.printhub_3d.dto.custom_prints.CustomPrintServiceResponseDTO;
import com.fpt.printhub_3d.entity.CustomPrintService;
import com.fpt.printhub_3d.entity.User;
import com.fpt.printhub_3d.repository.CustomPrintServiceRepository;
import com.fpt.printhub_3d.repository.specification.CustomPrintServiceSpecification;
import com.fpt.printhub_3d.service.CustomPrintManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomPrintManagementServiceImpl implements CustomPrintManagementService {

    private final CustomPrintServiceRepository customPrintServiceRepository;

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "minimumPrice", "estimatedProductionDays", "createdAt", "serviceName"
    );
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 50;

    @Override
    @Transactional
    public CustomPrintServiceResponseDTO createService(CustomPrintServiceRequestDTO request, User maker) {
        log.info("Maker [{}] đang tạo gói dịch vụ in: {}", maker.getId(), request.serviceName());

        CustomPrintService entity = CustomPrintService.builder()
                .maker(maker)
                .serviceName(request.serviceName())
                .description(request.description())
                .printerModels(new ArrayList<>(request.printerModels()))
                .supportedMaterials(new ArrayList<>(request.supportedMaterials()))
                .minimumPrice(request.minimumPrice())
                .maxPrintSize(request.maxPrintSize())
                .estimatedProductionDays(request.estimatedProductionDays())
                .build();

        CustomPrintService saved = customPrintServiceRepository.save(entity);

        log.info("Tạo gói dịch vụ in thành công với ID: {}", saved.getId());

        return mapToResponseDTO(saved);
    }

    @Override
    public Page<CustomPrintServiceResponseDTO> getServices(CustomPrintServiceFilterDTO filter) {
        log.info("Tìm kiếm dịch vụ in với bộ lọc: keyword={}, material={}, minPrice={}, maxPrice={}",
                filter.keyword(), filter.material(), filter.minPrice(), filter.maxPrice());

        Pageable pageable = buildPageable(filter);
        Specification<CustomPrintService> spec = CustomPrintServiceSpecification.withFilter(filter);

        Page<CustomPrintService> page = customPrintServiceRepository.findAll(spec, pageable);

        log.info("Tìm thấy {} dịch vụ in (trang {}/{})",
                page.getTotalElements(), page.getNumber() + 1, page.getTotalPages());

        return page.map(this::mapToResponseDTO);
    }

    // ──────────────────────────── Private helpers ────────────────────────────

    private Pageable buildPageable(CustomPrintServiceFilterDTO filter) {
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

    private CustomPrintServiceResponseDTO mapToResponseDTO(CustomPrintService entity) {
        return CustomPrintServiceResponseDTO.builder()
                .id(entity.getId())
                .makerId(entity.getMaker().getId())
                .makerName(entity.getMaker().getFullName())
                .serviceName(entity.getServiceName())
                .description(entity.getDescription())
                .printerModels(entity.getPrinterModels())
                .supportedMaterials(entity.getSupportedMaterials())
                .minimumPrice(entity.getMinimumPrice())
                .maxPrintSize(entity.getMaxPrintSize())
                .estimatedProductionDays(entity.getEstimatedProductionDays())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
