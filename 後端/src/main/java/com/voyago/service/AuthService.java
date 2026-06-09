package com.voyago.service;

import com.voyago.dto.*;

public interface AuthService {
    AuthResponse register(RegisterRequest req);
    AuthResponse login(LoginRequest req);
    AuthResponse googleLogin(GoogleLoginRequest req);
}
