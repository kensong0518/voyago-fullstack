package com.voyago.repository;

import com.voyago.entity.Favorite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

    @Query("SELECT f FROM Favorite f JOIN FETCH f.route WHERE f.member.id = :memberId ORDER BY f.createdAt DESC")
    List<Favorite> findByMember(@Param("memberId") Long memberId);

    Optional<Favorite> findByMemberIdAndRouteId(Long memberId, Long routeId);

    boolean existsByMemberIdAndRouteId(Long memberId, Long routeId);

    void deleteByMemberIdAndRouteId(Long memberId, Long routeId);
}
