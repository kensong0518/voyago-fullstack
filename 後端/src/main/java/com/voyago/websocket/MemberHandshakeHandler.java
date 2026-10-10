package com.voyago.websocket;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;

/**
 * 把 {@link JwtHandshakeInterceptor} 放進 attributes 的 memberId
 * 轉成 STOMP session 的 Principal，讓 SimpMessagingTemplate 可以用
 * convertAndSendToUser(memberId, ...) 把訊息推給正確的使用者。
 */
public class MemberHandshakeHandler extends DefaultHandshakeHandler {

    @Override
    protected Principal determineUser(ServerHttpRequest request, WebSocketHandler wsHandler,
                                       Map<String, Object> attributes) {
        Object memberId = attributes.get("memberId");
        if (memberId == null) {
            return super.determineUser(request, wsHandler, attributes);
        }
        return new MemberPrincipal(String.valueOf(memberId));
    }

    private record MemberPrincipal(String name) implements Principal {
        @Override
        public String getName() {
            return name;
        }
    }
}
