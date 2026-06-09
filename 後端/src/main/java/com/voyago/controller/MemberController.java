package com.voyago.controller;

import com.voyago.dto.MemberDto;
import com.voyago.dto.ProfileUpdateRequest;
import com.voyago.security.CurrentUser;
import com.voyago.service.MemberService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/members")
public class MemberController {

    private final MemberService members;

    public MemberController(MemberService members) {
        this.members = members;
    }

    @PutMapping("/me")
    public MemberDto updateMe(@Valid @RequestBody ProfileUpdateRequest req) {
        Long id = CurrentUser.id();
        if (id == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "請先登入");
        return members.updateProfile(id, req);
    }
}
