package com.voyago.repository;

import com.voyago.entity.Member;
import java.util.List;
import java.util.Optional;

/** 會員 DAO — 依 Email 查詢、列表分頁、計數。 */
public interface MemberDao {
    Optional<Member> findByEmail(String email);
    boolean existsByEmail(String email);

    /** STAFF 後台用：列出會員，q 不空時用模糊比對 name/email/phone。 */
    List<Member> search(String q, int page, int size);
    long count(String q);
}
