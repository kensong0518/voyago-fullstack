package com.voyago.dto;

import com.voyago.entity.Member;

public record MemberDto(Long id, String name, String email, String role, String provider, String avatarUrl) {
    public static MemberDto from(Member m) {
        return new MemberDto(m.getId(), m.getName(), m.getEmail(), m.getRole(), m.getProvider(), m.getAvatarUrl());
    }
}
