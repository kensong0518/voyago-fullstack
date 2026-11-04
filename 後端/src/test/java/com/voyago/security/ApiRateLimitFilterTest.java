package com.voyago.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

class ApiRateLimitFilterTest {

    private final ApiRateLimitFilter filter = new ApiRateLimitFilter(new ClientIpResolver("", false, ""), 5, 2);

    private int call(String method, String path, String ip) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest(method, path);
        req.setRequestURI(path);
        req.setRemoteAddr(ip);
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain());
        return res.getStatus();
    }

    @Test
    void writesHaveTheirOwnLowerLimit() throws Exception {
        assertEquals(200, call("POST", "/api/favorites/1", "1.1.1.1"));
        assertEquals(200, call("DELETE", "/api/favorites/1", "1.1.1.1"));
        assertEquals(429, call("POST", "/api/favorites/1", "1.1.1.1"));
        assertEquals(200, call("GET", "/api/routes", "1.1.1.1"));   // 讀取還在總額度內
    }

    @Test
    void totalLimit_perClient_andOtherClientsUnaffected() throws Exception {
        for (int i = 0; i < 5; i++) assertEquals(200, call("GET", "/api/routes", "2.2.2.2"));
        assertEquals(429, call("GET", "/api/routes", "2.2.2.2"));
        assertEquals(200, call("GET", "/api/routes", "3.3.3.3"));
    }

    @Test
    void pagesAndEndpointsWithTheirOwnLimiter_areSkipped() throws Exception {
        for (int i = 0; i < 10; i++) {
            assertEquals(200, call("GET", "/tours/paris", "4.4.4.4"));
            assertEquals(200, call("POST", "/api/auth/login", "4.4.4.4"));
        }
    }
}
