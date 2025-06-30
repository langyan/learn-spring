package com.lin.spring.otp.repository;

import com.lin.spring.otp.entity.Otp;
import com.lin.spring.otp.enums.OtpType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OtpRepository extends JpaRepository<Otp, Long> {
    Optional<Otp> findByCodeAndUser_IdAndTypeAndVerifiedIsFalseAndExpiresAtAfter(
            String code, Long userId, OtpType type, LocalDateTime now);

    List<Otp> findByUser_IdAndVerifiedIsFalseAndExpiresAtBefore(Long userId, LocalDateTime now);
}