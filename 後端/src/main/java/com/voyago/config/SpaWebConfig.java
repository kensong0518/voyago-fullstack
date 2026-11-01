package com.voyago.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 讓同一個 Spring Boot 服務同時提供前端網頁（Vue 建置產物）與 API，部署成單一網址。
 *
 * Docker 建置時把前端 dist/ 放進 classpath:/static/（見 backend.Dockerfile）。
 * Vue Router 用 history 模式，/tours/paris-loire、/admin 這類網址在伺服器上沒有對應檔案，
 * 必須回 index.html 交給前端路由處理；但 API、健康檢查、WebSocket 等後端路徑不能被吃掉，
 * 否則打錯 API 會拿到一頁 HTML 而不是 404，很難除錯。
 *
 * 本機只跑後端（沒有前端產物）時找不到 index.html，行為跟原本一樣回 404。
 */
@Configuration
public class SpaWebConfig implements WebMvcConfigurer {

    /** 這些開頭的路徑屬於後端，找不到就該 404，不回 index.html。 */
    private static final List<String> BACKEND_PREFIXES =
            List.of("api", "actuator", "ws-chat", "swagger-ui", "v3/api-docs", "error");

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Vite 產出的檔名帶內容雜湊，內容變了檔名就變，可以放心長期快取
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/assets/")
                .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable());

        // 其餘路徑：有檔案就給檔案，沒有就回 index.html；index.html 不快取，部署新版立刻生效
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .setCacheControl(CacheControl.noCache())
                .resourceChain(true)
                .addResolver(new SpaFallbackResolver());
    }

    static final class SpaFallbackResolver extends PathResourceResolver {
        @Override
        protected Resource getResource(String resourcePath, Resource location) throws IOException {
            Resource requested = location.createRelative(resourcePath);
            if (requested.exists() && requested.isReadable()) {
                return requested;
            }
            if (isBackendPath(resourcePath) || looksLikeFile(resourcePath)) {
                return null;   // 真的不存在：交給 Spring 回 404
            }
            Resource index = location.createRelative("index.html");
            return index.exists() && index.isReadable() ? index : null;
        }

        private static boolean isBackendPath(String path) {
            return BACKEND_PREFIXES.stream().anyMatch(p -> path.equals(p) || path.startsWith(p + "/"));
        }

        /** 最後一段有副檔名（logo.png、app.js）代表要的是檔案，找不到就是 404，不是前端路由。 */
        private static boolean looksLikeFile(String path) {
            String last = path.substring(path.lastIndexOf('/') + 1);
            return last.contains(".");
        }
    }
}
