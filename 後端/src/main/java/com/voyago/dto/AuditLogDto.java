package com.voyago.dto;

import com.voyago.entity.AuditLog;
import java.time.LocalDateTime;

public record AuditLogDto(Long id, String actorEmail, String action, String targetType, Long targetId,
                          String detail, LocalDateTime createdAt) {
    public static AuditLogDto from(AuditLog a) {
        return new AuditLogDto(a.getId(), a.getActorEmail(), a.getAction(), a.getTargetType(), a.getTargetId(),
                a.getDetail(), a.getCreatedAt());
    }
}
