package com.voyago.service;

import com.voyago.dto.MemberCreateRequest;
import com.voyago.dto.MemberDto;
import com.voyago.dto.PageResult;
import com.voyago.dto.ProfileUpdateRequest;
import com.voyago.entity.Member;
import com.voyago.repository.MemberDao;
import com.voyago.repository.MemberRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class MemberServiceImpl implements MemberService {

    private final MemberRepository members;
    private final MemberDao memberDao;
    private final PasswordEncoder encoder;

    public MemberServiceImpl(MemberRepository members, MemberDao memberDao, PasswordEncoder encoder) {
        this.members = members;
        this.memberDao = memberDao;
        this.encoder = encoder;
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

    @Override
    public PageResult<MemberDto> list(String q, int page, int size) {
        int p = Math.max(0, page);
        int s = size <= 0 ? 20 : Math.min(size, 100);
        long total = memberDao.count(q);
        int totalPages = (int) Math.ceil((double) total / s);
        List<MemberDto> content = memberDao.search(q, p, s).stream().map(MemberDto::from).toList();
        return new PageResult<>(content, p, s, total, totalPages);
    }

    @Override
    @Transactional
    public MemberDto create(MemberCreateRequest req) {
        if (memberDao.existsByEmail(req.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "此 Email 已被註冊");
        }
        Member m = new Member();
        m.setName(req.name());
        m.setEmail(req.email());
        m.setPhone(req.phone());
        m.setPassword(encoder.encode(req.password()));
        m.setRole(req.role() == null || req.role().isBlank() ? "MEMBER" : req.role());
        m.setProvider("LOCAL");
        members.save(m);
        return MemberDto.from(m);
    }

    @Override
    @Transactional
    public void deleteByStaff(Long actingMemberId, Long targetId) {
        if (actingMemberId != null && actingMemberId.equals(targetId)) {
            // STAFF 不能在管理介面把自己砍掉；要砍自己請走 /me（明確意圖）
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "請改用「刪除我的帳號」刪除自己");
        }
        Member m = members.findById(targetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到會員"));
        members.delete(m);
    }

    @Override
    @Transactional
    public void deleteSelf(Long memberId) {
        Member m = members.findById(memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到會員"));
        members.delete(m);
    }
}
