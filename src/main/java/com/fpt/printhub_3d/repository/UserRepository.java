package com.fpt.printhub_3d.repository;

import com.fpt.printhub_3d.entity.Enumeration.UserStatus;
import com.fpt.printhub_3d.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from User e where e.id = :id")
    Optional<User> findLockedById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.email = :email")
    Optional<User> findLockedByEmail(@Param("email") String email);

    boolean existsByEmail(String email);
    Optional<User> findByEmailOrUsername(String email, String username);
    Optional<User> findByEmail(String email);
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
    Optional<User> findByPhone(String phone);
    boolean existsByCccdNumber(String cccdNumber);
    Optional<User> findByCccdNumber(String cccdNumber);

    // Dùng cho Job Cleanup dọn dẹp tài khoản PENDING hết hạn
    List<User> findTop200ByStatusAndEmailVerifiedFalseAndRegistrationExpiresAtLessThanEqualOrderByRegistrationExpiresAtAsc(
            UserStatus status, Instant now
    );
}
