package com.voyago.service;

import com.voyago.dto.PageResult;
import com.voyago.dto.RouteDto;
import java.util.List;

/** 行程服務層介面（緯育課程慣例：介面 + Impl 分離）。 */
public interface RouteService {
    List<RouteDto> list(String q, String tag, String sort);
    PageResult<RouteDto> listPaged(String q, String tag, String sort, int page, int size);
    RouteDto getBySlug(String slug);
}
