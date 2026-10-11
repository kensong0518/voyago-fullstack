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

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 行程服務層實作：把請求參數整理成查詢條件、計算分頁總頁數、呼叫 DAO，再轉成 DTO 回傳。
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

    @Override
    @Cacheable(cacheNames = "routes", key = "'similar:' + #slug + '|' + #limit")
    public List<RouteDto> getSimilar(String slug, int limit) {
        Route base = routeDao.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到此行程"));
        Set<String> baseTags = tagSet(base.getTags());
        int take = limit <= 0 ? 3 : limit;

        return routeDao.search("", "", null).stream()
                .filter(r -> !r.getSlug().equals(base.getSlug()))
                .map(r -> new Scored(r, score(r, base, baseTags)))
                .filter(s -> s.score() > 0)
                .sorted(Comparator.<Scored>comparingDouble(Scored::score).reversed()
                        .thenComparing(s -> s.route().getRating(), Comparator.reverseOrder()))
                .limit(take)
                .map(s -> RouteDto.from(s.route()))
                .toList();
    }

    /**
     * 相似度計分：標籤交集數（權重 2）＋同國家（權重 1）。
     * 資料量小（demo 等級的行程數），直接在 service 層算分排序即可；
     * 真要擴量就改成 SQL 端算交集，或換成向量相似度。
     */
    private double score(Route candidate, Route base, Set<String> baseTags) {
        Set<String> candidateTags = tagSet(candidate.getTags());
        candidateTags.retainAll(baseTags);
        double s = candidateTags.size() * 2.0;
        if (candidate.getCountry() != null && candidate.getCountry().equalsIgnoreCase(base.getCountry())) {
            s += 1.0;
        }
        return s;
    }

    private Set<String> tagSet(String tags) {
        if (tags == null || tags.isBlank()) return Set.of();
        return java.util.Arrays.stream(tags.split(","))
                .map(String::trim).filter(t -> !t.isEmpty())
                .collect(Collectors.toCollection(java.util.HashSet::new));
    }

    private record Scored(Route route, double score) {
    }

    private String safe(String v) {
        return v == null ? "" : v.trim();
    }
}
