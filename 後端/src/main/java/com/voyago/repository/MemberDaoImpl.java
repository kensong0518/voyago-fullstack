package com.voyago.repository;

import com.voyago.entity.Member;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import org.springframework.stereotype.Repository;

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
}
