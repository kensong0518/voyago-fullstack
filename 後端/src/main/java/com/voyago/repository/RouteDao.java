package com.voyago.repository;

import com.voyago.entity.Route;
import java.util.List;
import java.util.Optional;

/**
 * 行程資料存取層（DAO）。
 * 依緯育課程慣例，以 Criteria 做動態複合查詢、分頁與計數。
 */
public interface RouteDao {
    Optional<Route> findBySlug(String slug);
    List<Route> search(String q, String tag, String sort);
    List<Route> searchPaged(String q, String tag, String sort, int page, int size);
    long count(String q, String tag);
}
