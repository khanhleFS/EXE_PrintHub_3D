package com.fpt.printhub_3d.repository;

import com.fpt.printhub_3d.entity.ShippingInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface ShippingInfoRepository extends JpaRepository<ShippingInfo, UUID> {
    List<ShippingInfo> findByIdIn(Collection<UUID> ids);
}
