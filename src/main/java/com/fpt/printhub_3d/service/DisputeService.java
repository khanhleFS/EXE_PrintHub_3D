package com.fpt.printhub_3d.service;

import com.fpt.printhub_3d.dto.dispute.*;
import com.fpt.printhub_3d.entity.User;

import java.util.List;
import java.util.UUID;

public interface DisputeService {
    DisputeResponseDTO createDispute(DisputeCreateRequestDTO request, User filedBy);
    DisputeResponseDTO resolveDispute(UUID id, DisputeResolutionRequestDTO request);
    List<DisputeDetailDTO> getMyDisputes(User user);
    List<DisputeDetailDTO> getAllDisputes();
    List<DisputeMessageResponseDTO> getMessages(UUID disputeId, User user);
    void sendMessage(UUID disputeId, DisputeMessageRequestDTO request, User user);
}
