package com.fpt.printhub_3d.service.impl;

import com.fpt.printhub_3d.common.exception.ApiException;
import com.fpt.printhub_3d.common.exception.CommonErrorCode;
import com.fpt.printhub_3d.dto.printer.PrinterRequestDTO;
import com.fpt.printhub_3d.dto.printer.PrinterResponseDTO;
import com.fpt.printhub_3d.entity.Printer;
import com.fpt.printhub_3d.repository.PrinterRepository;
import com.fpt.printhub_3d.service.PrinterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PrinterServiceImpl implements PrinterService {

    private final PrinterRepository printerRepository;

    private PrinterResponseDTO toDTO(Printer p) {
        return PrinterResponseDTO.builder()
                .id(p.getId())
                .name(p.getName())
                .type(p.getType())
                .status(p.getStatus())
                .note(p.getNote())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PrinterResponseDTO> getAllPrinters() {
        return printerRepository.findAllByOrderByNameAsc()
                .stream()
                .map(this::toDTO)
                .toList();
    }

    @Override
    public PrinterResponseDTO createPrinter(PrinterRequestDTO request) {
        Printer p = new Printer();
        p.setName(request.name());
        p.setType(request.type());
        p.setStatus(request.status());
        p.setNote(request.note());
        printerRepository.save(p);
        return toDTO(p);
    }

    @Override
    public PrinterResponseDTO updatePrinter(UUID id, PrinterRequestDTO request) {
        Printer p = printerRepository.findById(id)
                .orElseThrow(() -> new ApiException(CommonErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy máy in"));

        p.setName(request.name());
        p.setType(request.type());
        p.setStatus(request.status());
        p.setNote(request.note());
        printerRepository.save(p);
        return toDTO(p);
    }
}
