package com.fpt.printhub_3d.repository;

import com.fpt.printhub_3d.entity.ShippingInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ShippingInfoRepository extends JpaRepository<ShippingInfo, UUID> {
}
