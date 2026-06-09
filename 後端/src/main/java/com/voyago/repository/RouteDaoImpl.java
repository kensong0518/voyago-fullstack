package com.voyago.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.voyago.entity.Route;
import com.voyago.mapper.RouteMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 行程資料存取層 —— 改用 MyBatis-Plus 實作。
 * 以 LambdaQueryWrapper 組動態複合查詢、PaginationInnerInterceptor 做分頁、selectCount 計數。
 * （其他模組仍用 Spring Data JPA + Criteria，專案同時展示兩種 ORM。）
 */
@Repository
public class RouteDaoImpl implements RouteDao {

    private final RouteMapper routeMapper;

    public RouteDaoImpl(RouteMapper routeMapper) {
        this.routeMapper = routeMapper;
    }

    @Override
    public Optional<Route> findBySlug(String slug) {
        LambdaQueryWrapper<Route> w = new LambdaQueryWrapper<Route>().eq(Route::getSlug, slug);
        return Optional.ofNullable(routeMapper.selectOne(w));
    }

    @Override
    public List<Route> search(String q, String tag, String sort) {
        return routeMapper.selectList(wrapper(q, tag, sort));
    }

    @Override
    public List<Route> searchPaged(String q, String tag, String sort, int page, int size) {
        // MyBatis-Plus 的 Page 頁碼從 1 起算，外部傳入是從 0 起算
        Page<Route> p = new Page<>(Math.max(0, page) + 1, size);
        return routeMapper.selectPage(p, wrapper(q, tag, sort)).getRecords();
    }

    @Override
    public long count(String q, String tag) {
        return routeMapper.selectCount(wrapper(q, tag, null));
    }

    /** 把查詢條件組成 MyBatis-Plus 的 LambdaQueryWrapper（動態複合查詢）。 */
    private LambdaQueryWrapper<Route> wrapper(String q, String tag, String sort) {
        LambdaQueryWrapper<Route> w = new LambdaQueryWrapper<>();
        if (q != null && !q.isBlank()) {
            w.and(x -> x.like(Route::getName, q)
                        .or().like(Route::getCountry, q)
                        .or().like(Route::getLocation, q)
                        .or().like(Route::getSummary, q));
        }
        if (tag != null && !tag.isBlank()) {
            w.like(Route::getTags, tag);
        }
        switch (sort == null ? "" : sort) {
            case "price-asc" -> w.orderByAsc(Route::getPrice);
            case "price-desc" -> w.orderByDesc(Route::getPrice);
            case "rating" -> w.orderByDesc(Route::getRating);
            case "days" -> w.orderByAsc(Route::getDays);
            default -> w.orderByDesc(Route::getFeatured).orderByDesc(Route::getReviews);
        }
        return w;
    }
}
