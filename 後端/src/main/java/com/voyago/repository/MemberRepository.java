package com.voyago.repository;

import com.voyago.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByEmail(String email);
    boolean existsByEmail(String email);

    /** STAFF 後台統計：某時間點之後新增的會員數（用來算「本月新增」）。 */
    long countByCreatedAtAfter(LocalDateTime time);
}
