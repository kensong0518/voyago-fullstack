package com.voyago.controller;

import com.voyago.dto.PageResult;
import com.voyago.dto.RouteDto;
import com.voyago.service.RouteService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/routes")
public class RouteController {

    private final RouteService routes;

    public RouteController(RouteService routes) {
        this.routes = routes;
    }

    /** 行程列表（不分頁，相容前端 SPA）。 */
    @GetMapping
    public List<RouteDto> list(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "") String tag,
            @RequestParam(defaultValue = "featured") String sort) {
        return routes.list(q, tag, sort);
    }

    /** 分頁查詢（緯育慣例：回傳內容 + 頁碼 + 總頁數 + 總筆數）。 */
    @GetMapping("/page")
    public PageResult<RouteDto> listPaged(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "") String tag,
            @RequestParam(defaultValue = "featured") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "6") int size) {
        return routes.listPaged(q, tag, sort, page, size);
    }

    @GetMapping("/{slug}")
    public RouteDto detail(@PathVariable String slug) {
        return routes.getBySlug(slug);
    }
}
