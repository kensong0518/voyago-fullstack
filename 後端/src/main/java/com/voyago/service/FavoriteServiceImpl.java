package com.voyago.service;

import com.voyago.dto.FavoriteDto;
import com.voyago.entity.Favorite;
import com.voyago.entity.Member;
import com.voyago.entity.Route;
import com.voyago.repository.FavoriteRepository;
import com.voyago.repository.MemberRepository;
import com.voyago.repository.RouteRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class FavoriteServiceImpl implements FavoriteService {

    private final FavoriteRepository favorites;
    private final MemberRepository members;
    private final RouteRepository routes;

    public FavoriteServiceImpl(FavoriteRepository favorites, MemberRepository members, RouteRepository routes) {
        this.favorites = favorites;
        this.members = members;
        this.routes = routes;
    }

    @Override
    public List<FavoriteDto> listMine(Long memberId) {
        return favorites.findByMember(memberId).stream().map(FavoriteDto::from).toList();
    }

    @Override
    @Transactional
    public FavoriteDto add(Long memberId, Long routeId) {
        // 冪等：已收藏過直接回傳現有的，不重複新增（前端可能因網路重試打兩次）
        var existing = favorites.findByMemberIdAndRouteId(memberId, routeId);
        if (existing.isPresent()) {
            return FavoriteDto.from(existing.get());
        }
        Member member = members.findById(memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "請先登入"));
        Route route = routes.findById(routeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到此行程"));

        Favorite f = new Favorite();
        f.setMember(member);
        f.setRoute(route);
        try {
            favorites.save(f);
        } catch (DataIntegrityViolationException e) {
            // 併發下兩個請求同時新增同一筆收藏，unique constraint 擋下第二個——當成冪等成功處理
            return favorites.findByMemberIdAndRouteId(memberId, routeId)
                    .map(FavoriteDto::from)
                    .orElseThrow(() -> e);
        }
        return FavoriteDto.from(f);
    }

    @Override
    @Transactional
    public void remove(Long memberId, Long routeId) {
        favorites.deleteByMemberIdAndRouteId(memberId, routeId);
    }
}
