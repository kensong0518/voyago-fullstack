package com.voyago.service;

import com.voyago.dto.MemberCreateRequest;
import com.voyago.dto.MemberDto;
import com.voyago.dto.PageResult;
import com.voyago.dto.ProfileUpdateRequest;

public interface MemberService {
    MemberDto getById(Long id);
    MemberDto updateProfile(Long id, ProfileUpdateRequest req);

    /** STAFF 後台：分頁列出所有會員。 */
    PageResult<MemberDto> list(String q, int page, int size);

    /** STAFF 後台：新增會員（會驗證 email 唯一、密碼雜湊）。 */
    MemberDto create(MemberCreateRequest req);

    /** STAFF 後台：刪除指定會員。actingMemberId 是操作者，用來防止自殺。 */
    void deleteByStaff(Long actingMemberId, Long targetId);

    /** 使用者刪除自己的帳號。 */
    void deleteSelf(Long memberId);
}
