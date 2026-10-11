package com.voyago.service;

import com.voyago.dto.PageResult;
import com.voyago.dto.RouteDto;
import java.util.List;

/** 行程服務層介面（介面 + Impl 分離，便於替換實作與測試）。 */
public interface RouteService {
    List<RouteDto> list(String q, String tag, String sort);
    PageResult<RouteDto> listPaged(String q, String tag, String sort, int page, int size);
    RouteDto getBySlug(String slug);

    /** 依標籤重疊數＋同國家加權，找出與指定行程最相似的其他行程。 */
    List<RouteDto> getSimilar(String slug, int limit);
}
