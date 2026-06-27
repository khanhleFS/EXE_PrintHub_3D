package com.fpt.printhub_3d.service;

import com.fpt.printhub_3d.dto.dispute.DisputeCreateRequestDTO;
import com.fpt.printhub_3d.dto.dispute.DisputeResolutionRequestDTO;
import com.fpt.printhub_3d.dto.dispute.DisputeResponseDTO;
import com.fpt.printhub_3d.entity.User;

import java.util.UUID;

public interface DisputeService {
    DisputeResponseDTO createDispute(DisputeCreateRequestDTO request, User filedBy);

    DisputeResponseDTO resolveDispute(UUID id, DisputeResolutionRequestDTO request);
}
