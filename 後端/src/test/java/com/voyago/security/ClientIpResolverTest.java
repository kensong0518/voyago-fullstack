package com.voyago.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.*;

class ClientIpResolverTest {

    private static MockHttpServletRequest req(String remote, String xff, String cf) {
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.setRemoteAddr(remote);
        if (xff != null) r.addHeader("X-Forwarded-For", xff);
        if (cf != null) r.addHeader("CF-Connecting-IP", cf);
        return r;
    }

    @Test
    void defaultConfig_neverTrustsForwardedHeaders() {
        var resolver = new ClientIpResolver("", false, "");
        assertEquals("10.1.2.3", resolver.resolve(req("10.1.2.3", "203.0.113.9", null)));
    }

    @Test
    void privateNetworkProxy_usesFirstForwardedIp() {
        var resolver = new ClientIpResolver("", true, "");
        assertEquals("203.0.113.9", resolver.resolve(req("10.1.2.3", "203.0.113.9, 10.0.0.1", null)));
        assertEquals("203.0.113.9", resolver.resolve(req("100.64.5.6", "203.0.113.9", null)));   // CGNAT
    }

    @Test
    void publicRemote_isNotTrusted_evenWhenPrivateTrustIsOn() {
        // 從公網直接連進來（沒經過平台代理）的人自己帶 XFF，不能採信
        var resolver = new ClientIpResolver("", true, "");
        assertEquals("198.51.100.7", resolver.resolve(req("198.51.100.7", "1.2.3.4", null)));
    }

    @Test
    void explicitTrustedProxyList_stillWorks() {
        var resolver = new ClientIpResolver("198.51.100.7", false, "");
        assertEquals("1.2.3.4", resolver.resolve(req("198.51.100.7", "1.2.3.4", null)));
    }

    @Test
    void configuredClientIpHeader_winsOverXff() {
        var resolver = new ClientIpResolver("", true, "CF-Connecting-IP");
        assertEquals("203.0.113.50", resolver.resolve(req("10.1.2.3", "6.6.6.6", "203.0.113.50")));
        // 有指定可信 header 時完全不看 XFF（Render 會保留用戶端偽造的 XFF）；沒帶就用連線位址
        assertEquals("10.1.2.3", resolver.resolve(req("10.1.2.3", "6.6.6.6", null)));
    }

    @Test
    void garbageHeaderValues_areIgnored() {
        var resolver = new ClientIpResolver("", true, "");
        assertEquals("10.1.2.3", resolver.resolve(req("10.1.2.3", "evil.example.com", null)));
        assertEquals("10.1.2.3", resolver.resolve(req("10.1.2.3", "<script>", null)));
    }

    @Test
    void isPrivate_doesNotResolveHostnames() {
        assertFalse(ClientIpResolver.isPrivate("localhost"));
        assertTrue(ClientIpResolver.isPrivate("127.0.0.1"));
        assertTrue(ClientIpResolver.isPrivate("192.168.0.10"));
        assertTrue(ClientIpResolver.isPrivate("172.20.0.1"));
        assertFalse(ClientIpResolver.isPrivate("172.32.0.1"));
        assertFalse(ClientIpResolver.isPrivate("100.128.0.1"));
        assertTrue(ClientIpResolver.isPrivate("fd00::1"));
    }
}
