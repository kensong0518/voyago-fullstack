package com.voyago.service;

import com.voyago.dto.PageResult;
import com.voyago.dto.RouteDto;
import com.voyago.entity.Route;
import com.voyago.repository.RouteDao;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * 行程服務層實作。依緯育課程慣例：
 * 把請求參數整理成查詢條件、計算分頁總頁數、呼叫 DAO，再轉成 DTO 回傳。
 */
@Service
@Transactional(readOnly = true)
public class RouteServiceImpl implements RouteService {

    private final RouteDao routeDao;

    public RouteServiceImpl(RouteDao routeDao) {
        this.routeDao = routeDao;
    }

    @Override
    @Cacheable(cacheNames = "routes", key = "'list:' + #q + '|' + #tag + '|' + #sort")
    public List<RouteDto> list(String q, String tag, String sort) {
        return routeDao.search(safe(q), safe(tag), sort).stream().map(RouteDto::from).toList();
    }

    @Override
    @Cacheable(cacheNames = "routes",
               key = "'page:' + #q + '|' + #tag + '|' + #sort + '|' + #page + '|' + #size")
    public PageResult<RouteDto> listPaged(String q, String tag, String sort, int page, int size) {
        int p = Math.max(0, page);
        int s = size <= 0 ? 6 : size;
        long total = routeDao.count(safe(q), safe(tag));
        int totalPages = (int) Math.ceil((double) total / s);   // 計算總頁數
        List<RouteDto> content = routeDao.searchPaged(safe(q), safe(tag), sort, p, s)
                .stream().map(RouteDto::from).toList();
        return new PageResult<>(content, p, s, total, totalPages);
    }

    @Override
    @Cacheable(cacheNames = "routeBySlug", key = "#slug")
    public RouteDto getBySlug(String slug) {
        Route r = routeDao.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到此行程"));
        return RouteDto.from(r);
    }

    private String safe(String v) {
        return v == null ? "" : v.trim();
    }
}
