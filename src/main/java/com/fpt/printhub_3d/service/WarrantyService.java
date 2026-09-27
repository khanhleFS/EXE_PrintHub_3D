package com.fpt.printhub_3d.service;

import com.fpt.printhub_3d.dto.warranty.WarrantyClaimRequestDTO;
import com.fpt.printhub_3d.dto.warranty.WarrantyClaimResponseDTO;
import com.fpt.printhub_3d.dto.warranty.WarrantyClaimStatusRequestDTO;
import com.fpt.printhub_3d.entity.User;

import java.util.List;
import java.util.UUID;

public interface WarrantyService {
    List<WarrantyClaimResponseDTO> getClaimsByUser(User user);
    List<WarrantyClaimResponseDTO> getAllClaims();
    WarrantyClaimResponseDTO createClaim(User user, WarrantyClaimRequestDTO request);
    void updateClaimStatus(UUID id, WarrantyClaimStatusRequestDTO request, User admin);
}
