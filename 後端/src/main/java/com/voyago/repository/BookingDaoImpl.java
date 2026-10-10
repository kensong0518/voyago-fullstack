package com.voyago.repository;

import com.voyago.entity.Booking;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
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
        cq.select(cb.count(root)).where(visibleToMember(cb, root, memberId));
        return em.createQuery(cq).getSingleResult();
    }

    private jakarta.persistence.TypedQuery<Booking> query(Long memberId) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Booking> cq = cb.createQuery(Booking.class);
        Root<Booking> root = cq.from(Booking.class);
        root.fetch("route", JoinType.LEFT);          // 避免 N+1：列表時一次撈出 route
        cq.select(root).distinct(true)
          .where(visibleToMember(cb, root, memberId))
          .orderBy(cb.desc(root.get("createdAt")));
        return em.createQuery(cq);
    }

    /**
     * 「我的訂單」只顯示屬於該會員、且非已取消的訂單。
     * 取消的訂單仍留在資料表供稽核與對帳，只是從這個列表隱藏（見 BookingServiceImpl#cancel）。
     */
    private Predicate visibleToMember(CriteriaBuilder cb, Root<Booking> root, Long memberId) {
        return cb.and(
                cb.equal(root.get("member").get("id"), memberId),
                cb.notEqual(root.get("status"), "CANCELLED")
        );
    }
}
