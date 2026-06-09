package com.voyago.controller;

import com.voyago.dto.MessageDto;
import com.voyago.dto.MessageRequest;
import com.voyago.security.CurrentUser;
import com.voyago.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chat;

    public ChatController(ChatService chat) {
        this.chat = chat;
    }

    @GetMapping
    public List<MessageDto> myMessages() {
        return chat.listMine(CurrentUser.id());
    }

    @PostMapping
    public List<MessageDto> send(@Valid @RequestBody MessageRequest req) {
        return chat.send(CurrentUser.id(), req.content());
    }
}
