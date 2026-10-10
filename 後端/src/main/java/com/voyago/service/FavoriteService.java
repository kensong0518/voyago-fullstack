package com.voyago.service;

import com.voyago.dto.FavoriteDto;
import java.util.List;

public interface FavoriteService {
    List<FavoriteDto> listMine(Long memberId);
    FavoriteDto add(Long memberId, Long routeId);
    void remove(Long memberId, Long routeId);
}
