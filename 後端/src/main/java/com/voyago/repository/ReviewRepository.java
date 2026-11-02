package com.voyago.repository;

import com.voyago.entity.Review;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    Optional<Review> findByMemberIdAndRouteId(Long memberId, Long routeId);

    /** 行程頁的評價列表：一次帶出作者，避免逐筆 lazy load。 */
    @Query("SELECT r FROM Review r JOIN FETCH r.member WHERE r.route.id = :routeId ORDER BY r.updatedAt DESC, r.id DESC")
    List<Review> findLatestForRoute(@Param("routeId") Long routeId, Pageable pageable);

    /** 各星等的則數：[rating, count]。 */
    @Query("SELECT r.rating, COUNT(r) FROM Review r WHERE r.route.id = :routeId GROUP BY r.rating")
    List<Object[]> countByRating(@Param("routeId") Long routeId);
}
