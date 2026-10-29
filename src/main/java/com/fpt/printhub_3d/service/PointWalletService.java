package com.fpt.printhub_3d.service;

import com.fpt.printhub_3d.dto.point.PointTransactionResponseDTO;
import com.fpt.printhub_3d.dto.point.PointWalletResponseDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface PointWalletService {
    PointWalletResponseDTO getMyWallet(UUID userId);
    Page<PointTransactionResponseDTO> getMyTransactions(UUID userId, Pageable pageable);
}

