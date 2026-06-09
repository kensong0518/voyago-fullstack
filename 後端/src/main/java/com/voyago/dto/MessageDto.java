package com.voyago.dto;

import com.voyago.entity.Message;
import java.time.LocalDateTime;

public record MessageDto(Long id, String sender, String content, LocalDateTime createdAt) {
    public static MessageDto from(Message m) {
        return new MessageDto(m.getId(), m.getSender(), m.getContent(), m.getCreatedAt());
    }
}
