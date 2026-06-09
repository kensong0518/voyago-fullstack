package com.voyago.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Caffeine 純記憶體快取：路線資料幾乎不變，加 TTL 5 分鐘的快取
 * 可顯著降低 DB 查詢與 JSON 序列化負擔。
 *
 * Cache name：
 *   - routes         查詢列表 / 分頁結果（key = q+tag+sort+page+size）
 *   - routeBySlug    單筆查詢（key = slug）
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager mgr = new CaffeineCacheManager("routes", "routeBySlug");
        mgr.setCaffeine(Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .recordStats());
        return mgr;
    }
}
