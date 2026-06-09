package com.voyago.repository;

import com.voyago.entity.Message;
import java.util.List;

/** 客服訊息 DAO — 以 Criteria 依會員依時間升冪查詢。 */
public interface MessageDao {
    List<Message> findByMember(Long memberId);
}
