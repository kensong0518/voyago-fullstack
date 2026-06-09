package com.voyago.repository;

import com.voyago.entity.Booking;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Root;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class BookingDaoImpl implements BookingDao {

    @PersistenceContext
    private EntityManager em;

    @Override
    public List<Booking> findByMember(Long memberId) {
        return query(memberId).getResultList();
    }

    @Override
    public List<Booking> findByMemberPaged(Long memberId, int page, int size) {
        return query(memberId)
                .setFirstResult(Math.max(0, page) * size)
                .setMaxResults(size)
                .getResultList();
    }

    @Override
    public long countByMember(Long memberId) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Long> cq = cb.createQuery(Long.class);
        Root<Booking> root = cq.from(Booking.class);
        cq.select(cb.count(root)).where(cb.equal(root.get("member").get("id"), memberId));
        return em.createQuery(cq).getSingleResult();
    }

    private jakarta.persistence.TypedQuery<Booking> query(Long memberId) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Booking> cq = cb.createQuery(Booking.class);
        Root<Booking> root = cq.from(Booking.class);
        root.fetch("route", JoinType.LEFT);          // 避免 N+1：列表時一次撈出 route
        cq.select(root).distinct(true)
          .where(cb.equal(root.get("member").get("id"), memberId))
          .orderBy(cb.desc(root.get("createdAt")));
        return em.createQuery(cq);
    }
}
