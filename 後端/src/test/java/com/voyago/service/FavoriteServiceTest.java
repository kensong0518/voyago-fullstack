package com.voyago.service;

import com.voyago.dto.FavoriteDto;
import com.voyago.entity.Favorite;
import com.voyago.entity.Member;
import com.voyago.entity.Route;
import com.voyago.repository.FavoriteRepository;
import com.voyago.repository.MemberRepository;
import com.voyago.repository.RouteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FavoriteServiceTest {

    @Mock FavoriteRepository favorites;
    @Mock MemberRepository members;
    @Mock RouteRepository routes;

    private FavoriteServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new FavoriteServiceImpl(favorites, members, routes);
    }

    @Test
    void add_createsNewFavorite_whenNotExisting() {
        Member m = new Member(); m.setId(1L);
        Route r = new Route(); r.setId(2L); r.setName("測試行程");
        when(favorites.findByMemberIdAndRouteId(1L, 2L)).thenReturn(Optional.empty());
        when(members.findById(1L)).thenReturn(Optional.of(m));
        when(routes.findById(2L)).thenReturn(Optional.of(r));
        when(favorites.save(any(Favorite.class))).thenAnswer(inv -> inv.getArgument(0));

        FavoriteDto dto = service.add(1L, 2L);

        assertEquals("測試行程", dto.route().name());
        verify(favorites, times(1)).save(any(Favorite.class));
    }

    @Test
    void add_isIdempotent_whenAlreadyFavorited() {
        Member m = new Member(); m.setId(1L);
        Route r = new Route(); r.setId(2L); r.setName("測試行程");
        Favorite existing = new Favorite(); existing.setId(9L); existing.setMember(m); existing.setRoute(r);
        when(favorites.findByMemberIdAndRouteId(1L, 2L)).thenReturn(Optional.of(existing));

        FavoriteDto dto = service.add(1L, 2L);

        assertEquals(9L, dto.id());
        verify(favorites, never()).save(any());
    }

    @Test
    void remove_delegatesToRepository() {
        service.remove(1L, 2L);
        verify(favorites, times(1)).deleteByMemberIdAndRouteId(1L, 2L);
    }
}
