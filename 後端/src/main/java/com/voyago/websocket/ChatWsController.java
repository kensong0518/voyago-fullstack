package com.voyago.websocket;

import com.voyago.dto.MessageDto;
import com.voyago.dto.MessageRequest;
import com.voyago.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.List;

/**
 * 即時客服聊天的 STOMP 入口。沿用既有 ChatService（含自動回覆邏輯），
 * 差別只是結果改用 SimpMessagingTemplate 主動推送給該使用者，
 * 不再需要前端輪詢 / 重新打 REST API。
 */
@Controller
public class ChatWsController {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    public ChatWsController(ChatService chatService, SimpMessagingTemplate messagingTemplate) {
        this.chatService = chatService;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/chat.send")
    public void send(@Valid MessageRequest request, Principal principal) {
        if (principal == null) {
            return;
        }
        Long memberId = Long.valueOf(principal.getName());
        List<MessageDto> all = chatService.send(memberId, request.content());
        messagingTemplate.convertAndSendToUser(principal.getName(), "/queue/chat", all);
    }
}
