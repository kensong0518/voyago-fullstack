package com.voyago.service;

import com.voyago.dto.MemberDto;
import com.voyago.dto.ProfileUpdateRequest;
import com.voyago.entity.Member;
import com.voyago.repository.MemberRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class MemberServiceImpl implements MemberService {

    private final MemberRepository members;

    public MemberServiceImpl(MemberRepository members) {
        this.members = members;
    }

    @Override
    public MemberDto getById(Long id) {
        Member m = members.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到會員"));
        return MemberDto.from(m);
    }

    @Override
    @Transactional
    public MemberDto updateProfile(Long id, ProfileUpdateRequest req) {
        Member m = members.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到會員"));
        m.setName(req.name());
        if (req.phone() != null) m.setPhone(req.phone());
        members.save(m);
        return MemberDto.from(m);
    }
}
