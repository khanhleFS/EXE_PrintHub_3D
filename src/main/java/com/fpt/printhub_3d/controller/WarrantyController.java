package com.fpt.printhub_3d.controller;

import com.fpt.printhub_3d.entity.WarrantyClaim;
import com.fpt.printhub_3d.repository.WarrantyClaimRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/warranty")
@RequiredArgsConstructor
@CrossOrigin("*")
public class WarrantyController {

    private final WarrantyClaimRepository warrantyClaimRepository;

    @PostMapping("/claim")
    public ResponseEntity<WarrantyClaim> createClaim(@RequestBody WarrantyClaim claim) {
        claim.setStatus("PENDING");
        WarrantyClaim saved = warrantyClaimRepository.save(claim);
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<WarrantyClaim>> getUserClaims(@PathVariable UUID userId) {
        return ResponseEntity.ok(warrantyClaimRepository.findByUserId(userId));
    }

    @GetMapping("/admin/claims")
    public ResponseEntity<List<WarrantyClaim>> getAllClaims() {
        return ResponseEntity.ok(warrantyClaimRepository.findAll());
    }

    @PutMapping("/admin/claim/{id}/status")
    public ResponseEntity<WarrantyClaim> updateClaimStatus(@PathVariable UUID id, @RequestParam String status) {
        return warrantyClaimRepository.findById(id).map(claim -> {
            claim.setStatus(status);
            return ResponseEntity.ok(warrantyClaimRepository.save(claim));
        }).orElse(ResponseEntity.notFound().build());
    }
}
