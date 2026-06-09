package com.voyago.service;

import com.voyago.dto.MemberDto;
import com.voyago.dto.ProfileUpdateRequest;

public interface MemberService {
    MemberDto getById(Long id);
    MemberDto updateProfile(Long id, ProfileUpdateRequest req);
}
