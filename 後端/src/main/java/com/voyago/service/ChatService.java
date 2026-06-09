package com.voyago.service;

import com.voyago.dto.MessageDto;
import java.util.List;

public interface ChatService {
    List<MessageDto> listMine(Long memberId);
    List<MessageDto> send(Long memberId, String content);
}
