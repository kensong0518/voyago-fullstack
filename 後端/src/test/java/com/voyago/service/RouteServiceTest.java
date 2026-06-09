package com.voyago.service;

import com.voyago.dto.PageResult;
import com.voyago.dto.RouteDto;
import com.voyago.entity.Route;
import com.voyago.repository.RouteDao;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RouteServiceTest {

    @Mock RouteDao routeDao;
    @InjectMocks RouteServiceImpl service;

    private Route route(String slug, int price) {
        Route r = new Route();
        r.setSlug(slug); r.setName(slug); r.setSummary("s"); r.setDescription("d");
        r.setLocation("l"); r.setCountry("c"); r.setDays(5); r.setPrice(price);
        r.setRating(4.5); r.setReviews(10); r.setImageUrl("img"); r.setTags("a,b"); r.setFeatured(false);
        return r;
    }

    @Test
    void getBySlug_returnsDto_whenFound() {
        when(routeDao.findBySlug("paris")).thenReturn(Optional.of(route("paris", 50000)));
        RouteDto dto = service.getBySlug("paris");
        assertEquals("paris", dto.slug());
        assertEquals(50000, dto.price());
    }

    @Test
    void getBySlug_throws404_whenMissing() {
        when(routeDao.findBySlug("x")).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class, () -> service.getBySlug("x"));
    }

    @Test
    void list_mapsEntitiesToDtos() {
        when(routeDao.search("", "", "price-asc"))
                .thenReturn(List.of(route("a", 30000), route("b", 80000)));
        List<RouteDto> result = service.list("", "", "price-asc");
        assertEquals(2, result.size());
        assertEquals(30000, result.get(0).price());
    }

    @Test
    void listPaged_computesTotalPages() {
        when(routeDao.count("", "")).thenReturn(13L);
        when(routeDao.searchPaged("", "", "featured", 0, 6))
                .thenReturn(List.of(route("a", 1), route("b", 2)));
        PageResult<RouteDto> page = service.listPaged("", "", "featured", 0, 6);
        assertEquals(13, page.totalElements());
        assertEquals(3, page.totalPages());   // ceil(13 / 6) = 3
        assertEquals(0, page.page());
    }
}
