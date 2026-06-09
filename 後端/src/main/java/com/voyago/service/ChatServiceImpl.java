package com.voyago.service;

import com.voyago.dto.MessageDto;
import com.voyago.entity.Message;
import com.voyago.repository.MessageDao;
import com.voyago.repository.MessageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
@Transactional(readOnly = true)
public class ChatServiceImpl implements ChatService {

    private static final String[] AUTO_REPLIES = {
        "感謝您的訊息！客服專員會盡快為您處理 😊",
        "好的，已收到您的需求，我們為您查詢後回覆。",
        "這個行程目前還有名額喔，需要我幫您保留嗎？",
        "您可以在「會員中心」查看所有訂單狀態唷。"
    };

    private final MessageDao messageDao;             // 查詢（Criteria）
    private final MessageRepository messages;        // 寫入

    public ChatServiceImpl(MessageDao messageDao, MessageRepository messages) {
        this.messageDao = messageDao;
        this.messages = messages;
    }

    @Override
    public List<MessageDto> listMine(Long memberId) {
        return messageDao.findByMember(memberId).stream().map(MessageDto::from).toList();
    }

    @Override
    @Transactional
    public List<MessageDto> send(Long memberId, String content) {
        Message mine = new Message();
        mine.setMemberId(memberId);
        mine.setSender("MEMBER");
        mine.setContent(content);
        messages.save(mine);

        Message reply = new Message();
        reply.setMemberId(memberId);
        reply.setSender("STAFF");
        reply.setContent(AUTO_REPLIES[ThreadLocalRandom.current().nextInt(AUTO_REPLIES.length)]);
        messages.save(reply);

        return listMine(memberId);
    }
}
