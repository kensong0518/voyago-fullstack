package com.voyago.service;

import com.voyago.dto.AuditLogDto;
import com.voyago.dto.PageResult;
import com.voyago.entity.AuditLog;
import com.voyago.repository.AuditLogRepository;
import com.voyago.repository.MemberRepository;
import com.voyago.security.CurrentUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 寫操作紀錄。跟著呼叫端的交易走（MANDATORY 以外的預設 REQUIRED）：
 * 操作本身失敗回滾時，紀錄也一起消失，不會留下「做了但其實沒成功」的假紀錄。
 */
@Service
public class AuditService {

    private final AuditLogRepository logs;
    private final MemberRepository members;
    private final com.voyago.security.ClientIpResolver clientIp;

    public AuditService(AuditLogRepository logs, MemberRepository members,
                        com.voyago.security.ClientIpResolver clientIp) {
        this.logs = logs;
        this.members = members;
        this.clientIp = clientIp;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void record(String action, String targetType, Long targetId, String detail) {
        Long actor = CurrentUser.id();
        AuditLog a = new AuditLog();
        a.setActorId(actor);
        a.setActorEmail(actor == null ? "system" : members.findById(actor).map(m -> m.getEmail()).orElse("#" + actor));
        a.setAction(action);
        a.setTargetType(targetType);
        a.setTargetId(targetId);
        String d = detail == null ? "" : detail.replaceAll("\\s+", " ").trim();
        a.setDetail(d.length() > 500 ? d.substring(0, 499) + "…" : d);
        logs.save(a);
    }

    /**
     * 資安事件（登入成功／失敗／鎖定、權限不足、登出所有裝置）。
     * 用獨立交易（REQUIRES_NEW）：登入失敗時外面的交易會因為丟例外而回滾，紀錄不能跟著不見。
     * 會附上來源 IP，方便判斷是不是同一個來源在猜密碼。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void security(String action, Long actorId, String actorEmail, String detail) {
        AuditLog a = new AuditLog();
        a.setActorId(actorId);
        String email = actorEmail == null || actorEmail.isBlank() ? "unknown" : actorEmail.trim();
        a.setActorEmail(email.length() > 120 ? email.substring(0, 120) : email);
        a.setAction(action);
        a.setTargetType("SECURITY");
        a.setTargetId(actorId);
        String ip = currentIp();
        String d = ((detail == null ? "" : detail) + (ip == null ? "" : "（IP " + ip + "）")).replaceAll("\\s+", " ").trim();
        a.setDetail(d.length() > 500 ? d.substring(0, 499) + "…" : d);
        logs.save(a);
    }

    /** 目前登入者觸發的資安事件（例如一般會員嘗試打後台 API）。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void securityForCurrentUser(String action, String detail) {
        Long actor = CurrentUser.id();
        String email = actor == null ? "anonymous" : members.findById(actor).map(m -> m.getEmail()).orElse("#" + actor);
        security(action, actor, email, detail);
    }

    private String currentIp() {
        var attrs = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        if (attrs instanceof org.springframework.web.context.request.ServletRequestAttributes sra && clientIp != null) {
            return clientIp.resolve(sra.getRequest());
        }
        return null;
    }

    @Transactional(readOnly = true)
    public PageResult<AuditLogDto> list(int page, int size) {
        return list(page, size, "");
    }

    /** category：空字串＝全部、SECURITY＝只看資安事件、ADMIN＝只看後台操作。 */
    @Transactional(readOnly = true)
    public PageResult<AuditLogDto> list(int page, int size, String category) {
        int p = Math.max(0, page);
        int s = size <= 0 ? 20 : Math.min(size, 100);
        PageRequest pr = PageRequest.of(p, s);
        Page<AuditLog> r = switch (category == null ? "" : category.trim().toUpperCase()) {
            case "SECURITY" -> logs.findByTargetTypeOrderByCreatedAtDescIdDesc("SECURITY", pr);
            case "ADMIN" -> logs.findByTargetTypeNotOrderByCreatedAtDescIdDesc("SECURITY", pr);
            default -> logs.findAllByOrderByCreatedAtDescIdDesc(pr);
        };
        return new PageResult<>(r.getContent().stream().map(AuditLogDto::from).toList(), p, s,
                r.getTotalElements(), r.getTotalPages());
    }
}
