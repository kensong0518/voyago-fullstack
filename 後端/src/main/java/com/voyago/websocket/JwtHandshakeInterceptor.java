package com.voyago.websocket;

import com.voyago.security.JwtUtil;
import com.voyago.security.SessionValidator;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * WebSocket 升級前驗證 JWT。瀏覽器原生 WebSocket 無法自訂 Authorization header，
 * 所以 token 改走 query string（?token=...），驗證成功後把 memberId 放進
 * attributes，交給 {@link MemberHandshakeHandler} 轉成 STOMP 的 Principal。
 */
@Component
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtUtil jwtUtil;
    private final SessionValidator sessions;

    public JwtHandshakeInterceptor(JwtUtil jwtUtil, SessionValidator sessions) {
        this.jwtUtil = jwtUtil;
        this.sessions = sessions;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                    WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = extractToken(request);
        var session = token == null ? java.util.Optional.<SessionValidator.Session>empty()
                : jwtUtil.verify(token).flatMap(sessions::validate);   // 跟 REST 一樣檢查帳號、版本、角色
        if (session.isEmpty()) {
            response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
            return false;
        }
        attributes.put("memberId", session.get().memberId());
        attributes.put("role", session.get().role());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                WebSocketHandler wsHandler, Exception exception) {
        // 不需要事後處理
    }

    private String extractToken(ServerHttpRequest request) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            return null;
        }
        String query = servletRequest.getServletRequest().getQueryString();
        if (query == null) {
            return null;
        }
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && "token".equals(pair.substring(0, eq))) {
                return pair.substring(eq + 1);
            }
        }
        return null;
    }
}
