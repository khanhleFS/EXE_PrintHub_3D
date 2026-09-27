package com.fpt.printhub_3d.repository;

import com.fpt.printhub_3d.entity.Address;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AddressRepository extends JpaRepository<Address, Long> {
    List<Address> findByUserIdOrderByIdAsc(UUID userId);
    Optional<Address> findByIdAndUserId(Long id, UUID userId);
}
