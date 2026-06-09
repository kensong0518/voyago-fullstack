package com.voyago.controller;

import com.voyago.dto.*;
import com.voyago.security.CurrentUser;
import com.voyago.service.AuthService;
import com.voyago.service.MemberService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;
    private final MemberService members;

    public AuthController(AuthService auth, MemberService members) {
        this.auth = auth;
        this.members = members;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(auth.register(req));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        return auth.login(req);
    }

    @PostMapping("/google")
    public AuthResponse google(@Valid @RequestBody GoogleLoginRequest req) {
        return auth.googleLogin(req);
    }

    @GetMapping("/me")
    public Map<String, Object> me() {
        Long id = CurrentUser.id();
        Map<String, Object> body = new HashMap<>();
        body.put("user", id == null ? null : members.getById(id));
        return body;
    }
}
