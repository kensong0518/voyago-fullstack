package com.voyago.service;

import com.voyago.dto.*;

public interface AuthService {
    AuthResponse register(RegisterRequest req);
    AuthResponse login(LoginRequest req);
    AuthResponse googleLogin(GoogleLoginRequest req);

    /** 讓這個帳號所有已發出的登入憑證失效（所有裝置都要重新登入）。 */
    void logoutAllDevices(Long memberId);
}
