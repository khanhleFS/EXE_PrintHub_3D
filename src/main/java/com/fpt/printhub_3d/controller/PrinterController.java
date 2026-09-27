package com.fpt.printhub_3d.controller;

import com.fpt.printhub_3d.common.response.ApiResponse;
import com.fpt.printhub_3d.controller.api.PrinterAPI;
import com.fpt.printhub_3d.dto.printer.PrinterRequestDTO;
import com.fpt.printhub_3d.dto.printer.PrinterResponseDTO;
import com.fpt.printhub_3d.service.PrinterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
@CrossOrigin("*")
public class PrinterController implements PrinterAPI {

    private final PrinterService printerService;

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<PrinterResponseDTO>>> getAllPrinters() {
        List<PrinterResponseDTO> result = printerService.getAllPrinters();
        return ResponseEntity.ok(ApiResponse.<List<PrinterResponseDTO>>builder()
                .code(200)
                .message("Lấy danh sách máy in thành công")
                .result(result)
                .build());
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PrinterResponseDTO>> createPrinter(PrinterRequestDTO request) {
        PrinterResponseDTO result = printerService.createPrinter(request);
        return ResponseEntity.ok(ApiResponse.<PrinterResponseDTO>builder()
                .code(200)
                .message("Thêm máy in thành công")
                .result(result)
                .build());
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PrinterResponseDTO>> updatePrinter(UUID id, PrinterRequestDTO request) {
        PrinterResponseDTO result = printerService.updatePrinter(id, request);
        return ResponseEntity.ok(ApiResponse.<PrinterResponseDTO>builder()
                .code(200)
                .message("Cập nhật thông tin máy in thành công")
                .result(result)
                .build());
    }
}
