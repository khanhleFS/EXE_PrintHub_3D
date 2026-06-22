package com.fpt.printhub_3d.service;

import com.fpt.printhub_3d.dto.custom_prints.CustomPrintServiceFilterDTO;
import com.fpt.printhub_3d.dto.custom_prints.CustomPrintServiceRequestDTO;
import com.fpt.printhub_3d.dto.custom_prints.CustomPrintServiceResponseDTO;
import com.fpt.printhub_3d.entity.User;
import org.springframework.data.domain.Page;

public interface CustomPrintManagementService {

    /**
     * Tạo mới gói dịch vụ in 3D cho maker.
     *
     * @param request thông số kỹ thuật xưởng in
     * @param maker   user đang đăng nhập (có role MAKER)
     * @return thông tin dịch vụ vừa tạo
     */
    CustomPrintServiceResponseDTO createService(CustomPrintServiceRequestDTO request, User maker);

    /**
     * Duyệt, tìm kiếm và lọc danh sách dịch vụ in 3D đang hoạt động.
     *
     * @param filter bộ lọc tìm kiếm (keyword, material, price range, pagination)
     * @return trang kết quả danh sách dịch vụ
     */
    Page<CustomPrintServiceResponseDTO> getServices(CustomPrintServiceFilterDTO filter);
}
