package com.example.identity_service.session;

import org.springframework.data.jpa.repository.JpaRepository;

interface RotatedRefreshTokenRepository extends JpaRepository<RotatedRefreshToken, String> {
}
