package com.voyago.repository;

import com.voyago.entity.AiLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface AiLogRepository extends JpaRepository<AiLog, Long> {
    List<AiLog> findByCreatedAtGreaterThanEqualOrderByCreatedAtDesc(LocalDateTime since, Pageable limit);

    @Modifying
    @Query("delete from AiLog a where a.createdAt < :before")
    int deleteOlderThan(LocalDateTime before);
}
