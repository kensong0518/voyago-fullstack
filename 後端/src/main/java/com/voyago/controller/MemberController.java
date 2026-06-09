package com.voyago.controller;

import com.voyago.dto.MemberCreateRequest;
import com.voyago.dto.MemberDto;
import com.voyago.dto.PageResult;
import com.voyago.dto.ProfileUpdateRequest;
import com.voyago.security.CurrentUser;
import com.voyago.service.MemberService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/members")
public class MemberController {

    private final MemberService members;

    public MemberController(MemberService members) {
        this.members = members;
    }

    // ---------- 個人 ----------

    @PutMapping("/me")
    public MemberDto updateMe(@Valid @RequestBody ProfileUpdateRequest req) {
        Long id = requireLogin();
        return members.updateProfile(id, req);
    }

    @DeleteMapping("/me")
    public Map<String, Boolean> deleteMe() {
        Long id = requireLogin();
        members.deleteSelf(id);
        return Map.of("ok", true);
    }

    // ---------- STAFF 後台 ----------

    /** 分頁列出會員。q 為搜尋字（name/email/phone）。 */
    @GetMapping
    @PreAuthorize("hasRole('STAFF')")
    public PageResult<MemberDto> list(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return members.list(q, page, size);
    }

    @PostMapping
    @PreAuthorize("hasRole('STAFF')")
    public ResponseEntity<MemberDto> create(@Valid @RequestBody MemberCreateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(members.create(req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('STAFF')")
    public Map<String, Boolean> deleteByStaff(@PathVariable Long id) {
        members.deleteByStaff(requireLogin(), id);
        return Map.of("ok", true);
    }

    // ---------- helper ----------

    private Long requireLogin() {
        Long id = CurrentUser.id();
        if (id == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "請先登入");
        return id;
    }
}
