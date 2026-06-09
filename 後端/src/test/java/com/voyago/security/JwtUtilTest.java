package com.voyago.security;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    private final JwtUtil jwtUtil =
            new JwtUtil("test-secret-key-that-is-long-enough-for-hs256-algorithm", 3600000);

    @Test
    void generate_then_parse_returnsSameClaims() {
        String token = jwtUtil.generate(42L, "demo@voyago.com", "MEMBER");
        assertTrue(jwtUtil.isValid(token));
        assertEquals(42L, jwtUtil.getMemberId(token));
        assertEquals("MEMBER", jwtUtil.getRole(token));
    }

    @Test
    void isValid_returnsFalse_forGarbageToken() {
        assertFalse(jwtUtil.isValid("not-a-real-token"));
    }
}
