package com.fpt.printhub_3d.repository;

import com.fpt.printhub_3d.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    boolean existsByEmail(String email);
    Optional<User> findByEmailOrUsername(String email, String username);
    Optional<User> findByEmail(String email);
    Optional<User> findByPhone(String phone);
    boolean existsByCccdNumber(String cccdNumber);
    Optional<User> findByCccdNumber(String cccdNumber);
}
