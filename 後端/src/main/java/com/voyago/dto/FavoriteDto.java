package com.voyago.dto;

import com.voyago.entity.Favorite;

/** 回傳收藏連同行程資料，前端不用再多打一次 /api/routes 查詳情。 */
public record FavoriteDto(Long id, RouteDto route) {
    public static FavoriteDto from(Favorite f) {
        return new FavoriteDto(f.getId(), RouteDto.from(f.getRoute()));
    }
}
