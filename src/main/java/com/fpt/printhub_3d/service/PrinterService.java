package com.fpt.printhub_3d.service;

import com.fpt.printhub_3d.dto.printer.PrinterRequestDTO;
import com.fpt.printhub_3d.dto.printer.PrinterResponseDTO;

import java.util.List;
import java.util.UUID;

public interface PrinterService {
    List<PrinterResponseDTO> getAllPrinters();
    PrinterResponseDTO createPrinter(PrinterRequestDTO request);
    PrinterResponseDTO updatePrinter(UUID id, PrinterRequestDTO request);
}
