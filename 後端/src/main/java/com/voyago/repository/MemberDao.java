package com.voyago.repository;

import com.voyago.entity.Member;
import java.util.Optional;

/** 會員 DAO — 以 Criteria 依 Email 查詢。 */
public interface MemberDao {
    Optional<Member> findByEmail(String email);
    boolean existsByEmail(String email);
}
