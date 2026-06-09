package com.voyago.repository;

import com.voyago.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findByMemberIdOrderByCreatedAtAsc(Long memberId);
}
