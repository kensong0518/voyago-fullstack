package com.voyago.controller;

import com.voyago.dto.FavoriteDto;
import com.voyago.security.CurrentUser;
import com.voyago.service.FavoriteService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/favorites")
public class FavoriteController {

    private final FavoriteService favorites;

    public FavoriteController(FavoriteService favorites) {
        this.favorites = favorites;
    }

    @GetMapping
    public List<FavoriteDto> myFavorites() {
        return favorites.listMine(CurrentUser.id());
    }

    @PostMapping("/{routeId}")
    public ResponseEntity<FavoriteDto> add(@PathVariable Long routeId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(favorites.add(CurrentUser.id(), routeId));
    }

    @DeleteMapping("/{routeId}")
    public Map<String, Boolean> remove(@PathVariable Long routeId) {
        favorites.remove(CurrentUser.id(), routeId);
        return Map.of("ok", true);
    }
}
