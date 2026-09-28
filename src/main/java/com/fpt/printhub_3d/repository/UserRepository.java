package com.fpt.printhub_3d.repository;

import com.fpt.printhub_3d.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select e from User e where e.id = :id")
    java.util.Optional<User> findLockedById(@org.springframework.data.repository.query.Param("id") java.util.UUID id);

    boolean existsByEmail(String email);
    Optional<User> findByEmailOrUsername(String email, String username);
    Optional<User> findByEmail(String email);
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
    Optional<User> findByPhone(String phone);
    boolean existsByCccdNumber(String cccdNumber);
    Optional<User> findByCccdNumber(String cccdNumber);
}
