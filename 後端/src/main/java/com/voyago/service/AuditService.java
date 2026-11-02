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

    public AuditService(AuditLogRepository logs, MemberRepository members) {
        this.logs = logs;
        this.members = members;
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

    @Transactional(readOnly = true)
    public PageResult<AuditLogDto> list(int page, int size) {
        int p = Math.max(0, page);
        int s = size <= 0 ? 20 : Math.min(size, 100);
        Page<AuditLog> r = logs.findAllByOrderByCreatedAtDescIdDesc(PageRequest.of(p, s));
        return new PageResult<>(r.getContent().stream().map(AuditLogDto::from).toList(), p, s,
                r.getTotalElements(), r.getTotalPages());
    }
}
