package com.voyago.repository;

import com.voyago.entity.Member;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class MemberDaoImpl implements MemberDao {

    @PersistenceContext
    private EntityManager em;

    @Override
    public Optional<Member> findByEmail(String email) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Member> cq = cb.createQuery(Member.class);
        Root<Member> root = cq.from(Member.class);
        cq.select(root).where(cb.equal(root.get("email"), email));
        return em.createQuery(cq).getResultStream().findFirst();
    }

    @Override
    public boolean existsByEmail(String email) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Long> cq = cb.createQuery(Long.class);
        Root<Member> root = cq.from(Member.class);
        cq.select(cb.count(root)).where(cb.equal(root.get("email"), email));
        return em.createQuery(cq).getSingleResult() > 0;
    }

    @Override
    public List<Member> search(String q, int page, int size) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Member> cq = cb.createQuery(Member.class);
        Root<Member> root = cq.from(Member.class);
        cq.select(root).where(searchPredicate(cb, root, q))
          .orderBy(cb.desc(root.get("createdAt")));
        return em.createQuery(cq)
                .setFirstResult(Math.max(0, page) * size)
                .setMaxResults(size <= 0 ? 20 : size)
                .getResultList();
    }

    @Override
    public long count(String q) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Long> cq = cb.createQuery(Long.class);
        Root<Member> root = cq.from(Member.class);
        cq.select(cb.count(root)).where(searchPredicate(cb, root, q));
        return em.createQuery(cq).getSingleResult();
    }

    /** 動態 WHERE：q 為空 → 永真；非空 → name/email/phone like '%q%'。 */
    private Predicate searchPredicate(CriteriaBuilder cb, Root<Member> root, String q) {
        if (q == null || q.isBlank()) return cb.conjunction();
        String like = "%" + q.trim().toLowerCase() + "%";
        return cb.or(
            cb.like(cb.lower(root.get("name")), like),
            cb.like(cb.lower(root.get("email")), like),
            cb.like(cb.lower(root.get("phone")), like)
        );
    }
}
