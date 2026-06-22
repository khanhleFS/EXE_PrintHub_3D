package com.fpt.printhub_3d.repository;

import com.fpt.printhub_3d.entity.CustomPrintService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CustomPrintServiceRepository extends JpaRepository<CustomPrintService, UUID>,
        JpaSpecificationExecutor<CustomPrintService> {
    List<CustomPrintService> findByMakerId(UUID makerId);
}
