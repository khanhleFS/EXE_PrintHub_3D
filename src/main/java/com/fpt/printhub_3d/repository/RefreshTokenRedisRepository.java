package com.fpt.printhub_3d.repository;

import com.fpt.printhub_3d.entity.RefreshTokenRedis;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RefreshTokenRedisRepository extends CrudRepository<RefreshTokenRedis, String> {
    Optional<RefreshTokenRedis> findByToken(String token);
    void deleteByToken(String token);
}
