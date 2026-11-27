package com.example.identity_service.repository;

import com.example.identity_service.entity.RotatedRefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RotatedRefreshTokenRepository extends JpaRepository<RotatedRefreshToken, String> {
}
