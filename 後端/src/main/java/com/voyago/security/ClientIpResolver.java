package com.voyago.security;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 找出「真正的訪客 IP」，給限流（登入、AI 顧問）用。
 *
 * 部署在雲端平台時，請求都經過平台的反向代理才進到程式，getRemoteAddr() 拿到的是代理的位址；
 * 若直接拿它當限流依據，全站訪客會共用同一個額度（例如登入每分鐘 5 次變成全站共用）。
 * 但 X-Forwarded-For 又是用戶端可以自己帶的 header，不能無條件相信，否則換個假 IP 就能繞過限流。
 *
 * 規則：只有「直接連進來的那一方」是可信任的代理時，才採信它轉送的來源 IP：
 *   - 列在 TRUSTED_PROXIES 的位址；或
 *   - 開啟 TRUST_PRIVATE_NETWORK_PROXIES 時，來自私有網段（10.x、172.16–31.x、192.168.x、100.64/10、loopback）。
 *     適用於 Render 這類平台：容器只能透過平台代理連到，私有網段的連線一定是代理。
 * 採信時：
 *   - 有設定 app.security.client-ip-header（例如 CF-Connecting-IP）就只看它；沒帶就用 getRemoteAddr()，
 *     不退回 X-Forwarded-For。Render 前面是 Cloudflare：CF-Connecting-IP 由 Cloudflare 依實際連線覆寫，
 *     用戶端偽造不了；但 Render 的 X-Forwarded-For 會保留用戶端自己帶的值、只在後面附加，
 *     實測帶 X-Forwarded-For: 1.2.3.4 就能換到新的 AI 額度，所以正式站必須設 CLIENT_IP_HEADER。
 *   - 沒設定才用 X-Forwarded-For 的第一個位址（適用會清掉用戶端 header 的代理）。
 * 連線方不可信時一律用 getRemoteAddr()——跟原本最保守的行為相同。
 */
@Component
public class ClientIpResolver {

    private static final Logger log = LoggerFactory.getLogger(ClientIpResolver.class);

    private final Set<String> trustedProxies;
    private final boolean trustPrivateNetwork;
    private final String clientIpHeader;

    public ClientIpResolver(@Value("${app.security.trusted-proxies:}") String trustedProxiesRaw,
                            @Value("${app.security.trust-private-network-proxies:false}") boolean trustPrivateNetwork,
                            @Value("${app.security.client-ip-header:}") String clientIpHeader) {
        this.trustedProxies = Arrays.stream(trustedProxiesRaw.split(","))
                .map(String::trim).filter(s -> !s.isBlank())
                .collect(Collectors.toUnmodifiableSet());
        this.trustPrivateNetwork = trustPrivateNetwork;
        this.clientIpHeader = clientIpHeader == null ? "" : clientIpHeader.trim();
    }

    public String resolve(HttpServletRequest req) {
        String remote = req.getRemoteAddr();
        String resolved = remote;
        if (isTrustedProxy(remote)) {
            String fromProxy = clientIpHeader.isEmpty()
                    ? firstIp(req.getHeader("X-Forwarded-For"))
                    : firstIp(req.getHeader(clientIpHeader));
            if (fromProxy != null) resolved = fromProxy;
        }
        if (log.isDebugEnabled()) {
            // 部署後驗證平台實際送了哪些 header 用；平常不開（logging.level.com.voyago.security=DEBUG 才會印）
            log.debug("client-ip: remote={} trusted={} xff=[{}] {}=[{}] -> {}", remote, isTrustedProxy(remote),
                    req.getHeader("X-Forwarded-For"), clientIpHeader.isEmpty() ? "-" : clientIpHeader,
                    clientIpHeader.isEmpty() ? "" : req.getHeader(clientIpHeader), resolved);
        }
        return resolved;
    }

    boolean isTrustedProxy(String addr) {
        if (addr == null) return false;
        if (trustedProxies.contains(addr)) return true;
        return trustPrivateNetwork && isPrivate(addr);
    }

    /** 只接受 IP 字面值（不做 DNS 查詢）：拿 header 內容去解析網域名稱，等於讓用戶端叫伺服器去查 DNS。 */
    static boolean isPrivate(String addr) {
        if (!addr.matches("[0-9a-fA-F:.]+")) return false;
        try {
            InetAddress a = InetAddress.getByName(addr);
            if (a.isLoopbackAddress() || a.isSiteLocalAddress() || a.isLinkLocalAddress()) return true;
            byte[] b = a.getAddress();
            // 100.64.0.0/10（電信級 NAT，雲端平台內部常用）
            if (b.length == 4 && (b[0] & 0xFF) == 100 && (b[1] & 0xC0) == 64) return true;
            // IPv6 唯一本地位址 fc00::/7
            return b.length == 16 && (b[0] & 0xFE) == 0xFC;
        } catch (UnknownHostException e) {
            return false;
        }
    }

    private static String firstIp(String header) {
        if (header == null || header.isBlank()) return null;
        String first = header.split(",")[0].trim();
        return first.matches("[0-9a-fA-F:.]{2,45}") ? first : null;   // 不是 IP 格式就不採用
    }
}
