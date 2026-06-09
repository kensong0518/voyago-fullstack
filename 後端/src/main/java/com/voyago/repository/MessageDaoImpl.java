package com.voyago.repository;

import com.voyago.entity.Message;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class MessageDaoImpl implements MessageDao {

    @PersistenceContext
    private EntityManager em;

    /** 防呆上限：避免單一會員累積過多訊息時撐爆記憶體。 */
    private static final int HARD_LIMIT = 500;

    @Override
    public List<Message> findByMember(Long memberId) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Message> cq = cb.createQuery(Message.class);
        Root<Message> root = cq.from(Message.class);
        cq.select(root)
          .where(cb.equal(root.get("memberId"), memberId))
          .orderBy(cb.asc(root.get("createdAt")));
        return em.createQuery(cq).setMaxResults(HARD_LIMIT).getResultList();
    }
}
