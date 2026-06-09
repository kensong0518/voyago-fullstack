package com.voyago.repository;

import com.voyago.entity.Route;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface RouteRepository extends JpaRepository<Route, Long> {
    Optional<Route> findBySlug(String slug);
    List<Route> findByFeaturedTrueOrderByReviewsDesc();

    @Query("SELECT r FROM Route r WHERE " +
           "(:q = '' OR LOWER(r.name) LIKE LOWER(CONCAT('%', :q, '%')) " +
           " OR LOWER(r.country) LIKE LOWER(CONCAT('%', :q, '%')) " +
           " OR LOWER(r.location) LIKE LOWER(CONCAT('%', :q, '%')) " +
           " OR LOWER(r.summary) LIKE LOWER(CONCAT('%', :q, '%'))) " +
           "AND (:tag = '' OR r.tags LIKE CONCAT('%', :tag, '%'))")
    List<Route> search(@Param("q") String q, @Param("tag") String tag);
}
