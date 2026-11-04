package com.voyago.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.voyago.repository.MemberRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 每個帶 token 的請求都要通過這裡：簽章正確還不夠，還要
 *   1. 帳號仍存在（被刪除的帳號，手上的 token 立即失效）；
 *   2. token 的版本 = 資料庫目前的 token_version（「登出所有裝置」或帳號被回收後，舊 token 全部失效）；
 *   3. 角色以資料庫為準，不信 token 裡寫的（管理員被降級，下一個請求就沒有後台權限）。
 *
 * 每次都查資料庫太浪費，用 15 秒的小快取；撤銷時主動清掉該會員的快取，所以撤銷是立即生效的。
 */
@Component
public class SessionValidator {

    public record Session(Long memberId, String role) {}

    private record Snapshot(boolean exists, String role, int version) {}

    private final MemberRepository members;
    private final Cache<Long, Snapshot> cache = Caffeine.newBuilder()
            .maximumSize(50_000)
            .expireAfterWrite(15, TimeUnit.SECONDS)
            .build();

    public SessionValidator(MemberRepository members) {
        this.members = members;
    }

    public Optional<Session> validate(JwtUtil.Token token) {
        Snapshot s = cache.get(token.memberId(), id -> members.findById(id)
                .map(m -> new Snapshot(true, m.getRole(), m.getTokenVersion() == null ? 0 : m.getTokenVersion()))
                .orElse(new Snapshot(false, null, 0)));
        if (!s.exists() || s.version() != token.version()) return Optional.empty();
        return Optional.of(new Session(token.memberId(), s.role()));
    }

    /** 會員的版本、角色變了或帳號刪了，馬上讓快取失效。 */
    public void evict(Long memberId) {
        if (memberId != null) cache.invalidate(memberId);
    }
}
