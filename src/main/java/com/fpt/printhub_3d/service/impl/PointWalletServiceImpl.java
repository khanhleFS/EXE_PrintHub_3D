package com.fpt.printhub_3d.service.impl;

import com.fpt.printhub_3d.dto.point.PointTransactionResponseDTO;
import com.fpt.printhub_3d.dto.point.PointWalletResponseDTO;
import com.fpt.printhub_3d.entity.PointWallet;
import com.fpt.printhub_3d.repository.PointTransactionRepository;
import com.fpt.printhub_3d.repository.PointWalletRepository;
import com.fpt.printhub_3d.service.PointWalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PointWalletServiceImpl implements PointWalletService {

    private final PointWalletRepository pointWalletRepository;
    private final PointTransactionRepository pointTransactionRepository;

    @Override
    public PointWalletResponseDTO getMyWallet(UUID userId) {
        log.info("Lấy thông tin ví điểm cho user: {}", userId);
        PointWallet wallet = pointWalletRepository.findByUserId(userId).orElse(null);
        int balance = (wallet != null && wallet.getBalance() != null) ? wallet.getBalance() : 0;

        return PointWalletResponseDTO.builder()
                .userId(userId)
                .balance(balance)
                .availableToSpend(balance)
                .updatedAt(wallet != null ? wallet.getUpdatedAt() : null)
                .build();
    }

    @Override
    public Page<PointTransactionResponseDTO> getMyTransactions(UUID userId, Pageable pageable) {
        log.info("Lấy lịch sử giao dịch điểm cho user: {}", userId);
        return pointTransactionRepository.findByPointWallet_UserIdOrderByCreatedAtDesc(userId, pageable)
                .map(tx -> PointTransactionResponseDTO.builder()
                        .id(tx.getId())
                        .amount(tx.getAmount())
                        .type(tx.getType())
                        .description(tx.getDescription())
                        .createdAt(tx.getCreatedAt())
                        .build());
    }
}

