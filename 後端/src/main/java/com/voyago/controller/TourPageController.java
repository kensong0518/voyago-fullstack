package com.voyago.controller;

import com.voyago.dto.RouteDto;
import com.voyago.service.RouteService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 行程頁的分享預覽：LINE、FB、104 這類爬蟲只讀 HTML、不執行 JavaScript，
 * 所以 /tours/{slug} 由後端回傳「換上這個行程標題、介紹、封面圖」的 index.html，
 * 瀏覽器載入後照常由 Vue 接手。其他頁面用 index.html 裡的預設標籤（SpaWebConfig）。
 *
 * 只替換 index.html 裡已存在的標籤（格式見 前端/index.html 的註解），找不到就保持原樣；
 * 行程不存在時回原本的 index.html，讓前端顯示「找不到」頁面。
 */
@Controller
public class TourPageController {

    private static final String SITE = "Voyago 歐洲自助遊";

    private final RouteService routes;
    private final String publicUrl;
    private volatile String indexTemplate;   // 第一次請求時從 classpath 讀入，之後重用

    public TourPageController(RouteService routes,
                              @Value("${app.public-url}") String publicUrl) {
        this.routes = routes;
        // FRONTEND_ORIGIN 可能是逗號分隔的多個來源，取第一個；結尾的 / 去掉
        this.publicUrl = publicUrl.split(",")[0].trim().replaceAll("/+$", "");
    }

    @GetMapping(value = "/tours/{slug}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> tourPage(@PathVariable String slug) throws IOException {
        String html = indexHtml();
        if (html == null) {
            // 只跑後端、沒有打包前端時（本機開發）：維持原本的 404 行為
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
        }
        RouteDto r = findRoute(slug);
        if (r != null) {
            String title = r.name() + "｜" + SITE;
            String desc = r.summary() + "（" + r.country() + "・" + r.days() + " 天・每人 NT$" + String.format("%,d", r.price()) + " 起）";
            String url = publicUrl + "/tours/" + r.slug();
            html = replaceTitle(html, title);
            html = replaceMeta(html, "name", "description", desc);
            html = replaceMeta(html, "property", "og:type", "article");
            html = replaceMeta(html, "property", "og:title", title);
            html = replaceMeta(html, "property", "og:description", desc);
            html = replaceMeta(html, "property", "og:image", r.imageUrl());
            html = replaceMeta(html, "property", "og:url", url);
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .contentType(new MediaType("text", "html", StandardCharsets.UTF_8))
                .body(html);
    }

    private RouteDto findRoute(String slug) {
        try {
            return routes.getBySlug(slug);
        } catch (ResponseStatusException notFound) {
            return null;
        }
    }

    private String indexHtml() throws IOException {
        String cached = indexTemplate;
        if (cached != null) return cached;
        Resource res = new ClassPathResource("static/index.html");
        if (!res.exists()) return null;
        try (InputStream in = res.getInputStream()) {
            cached = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        indexTemplate = cached;
        return cached;
    }

    static String replaceTitle(String html, String title) {
        Matcher m = Pattern.compile("<title>[^<]*</title>").matcher(html);
        return m.find() ? m.replaceFirst(Matcher.quoteReplacement("<title>" + escape(title) + "</title>")) : html;
    }

    /** 換掉 {@code <meta attr="key" content="...">} 的 content；標籤不存在就不動。 */
    static String replaceMeta(String html, String attr, String key, String value) {
        Pattern p = Pattern.compile("(<meta\\s+" + attr + "=\"" + Pattern.quote(key) + "\"\\s+content=\")[^\"]*(\")");
        Matcher m = p.matcher(html);
        return m.find() ? m.replaceFirst(Matcher.quoteReplacement(m.group(1) + escape(value) + m.group(2))) : html;
    }

    /** 行程內容可由後台編輯，寫進 HTML 屬性前一定要跳脫，避免被注入標籤或腳本。 */
    static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;").replace("'", "&#39;");
    }
}
