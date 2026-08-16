package com.dms.dao;

import com.dms.models.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByToken(String token);

    /** Distinct users currently holding an unexpired, unrevoked session - the closest thing this system has to "active users". */
    @Query("select count(distinct rt.user.userId) from RefreshToken rt "
            + "where rt.revoked = false and rt.expiryDate > :now")
    long countDistinctActiveUsers(@Param("now") LocalDateTime now);
}