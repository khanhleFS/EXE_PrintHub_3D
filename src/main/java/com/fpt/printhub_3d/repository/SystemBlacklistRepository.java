package com.fpt.printhub_3d.repository;

import com.fpt.printhub_3d.entity.SystemBlacklist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SystemBlacklistRepository extends JpaRepository<SystemBlacklist, UUID> {
    boolean existsByCccdNumber(String cccdNumber);
    Optional<SystemBlacklist> findByCccdNumber(String cccdNumber);
}
