package com.fpt.printhub_3d.repository;

import com.fpt.printhub_3d.entity.Enumeration.PointTransactionType;
import com.fpt.printhub_3d.entity.PointTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PointTransactionRepository extends JpaRepository<PointTransaction, Long> {
    List<PointTransaction> findByPointWallet_UserIdOrderByCreatedAtDesc(UUID userId);
    Page<PointTransaction> findByPointWallet_UserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
    boolean existsBySourceTypeAndSourceIdAndRewardType(String sourceType, String sourceId, String rewardType);
    List<PointTransaction> findBySourceTypeAndSourceId(String sourceType, String sourceId);

    @EntityGraph(attributePaths = {"pointWallet", "pointWallet.user"})
    @Query("""
            select tx from PointTransaction tx
            where tx.type = :type and tx.amount < 0
              and (tx.sourceType = 'SUBSCRIPTION_REDEEM'
                   or (tx.sourceType is null and (tx.rewardType = 'SUBSCRIPTION_REDEEM'
                        or tx.description like :legacyDescription)))
            """)
    List<PointTransaction> findForSubscriptionHistory(@Param("type") PointTransactionType type,
                                                       @Param("legacyDescription") String legacyDescription);
}
