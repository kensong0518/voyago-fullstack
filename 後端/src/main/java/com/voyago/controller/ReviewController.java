package com.voyago.controller;

import com.voyago.dto.ReviewEligibilityDto;
import com.voyago.dto.ReviewRequest;
import com.voyago.dto.ReviewSummaryDto;
import com.voyago.security.CurrentUser;
import com.voyago.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/routes/{slug}/reviews")
public class ReviewController {

    private final ReviewService reviews;

    public ReviewController(ReviewService reviews) {
        this.reviews = reviews;
    }

    /** 公開：平均、分佈與最新評價（GET /api/routes/** 已在 SecurityConfig 開放）。 */
    @GetMapping
    public ReviewSummaryDto list(@PathVariable String slug) {
        return reviews.summary(slug);
    }

    /** 需登入：GET 雖然在 /api/routes/** 底下，沒登入時 CurrentUser 為 null，直接回「請先登入」。 */
    @GetMapping("/eligibility")
    public ReviewEligibilityDto eligibility(@PathVariable String slug) {
        Long me = CurrentUser.id();
        if (me == null) return new ReviewEligibilityDto(false, "登入並完成付款後即可評價", null, null);
        return reviews.eligibility(me, slug);
    }

    @PutMapping("/mine")
    public ReviewSummaryDto upsert(@PathVariable String slug, @Valid @RequestBody ReviewRequest req) {
        return reviews.upsert(CurrentUser.id(), slug, req);
    }

    @DeleteMapping("/mine")
    public ReviewSummaryDto delete(@PathVariable String slug) {
        return reviews.deleteMine(CurrentUser.id(), slug);
    }
}
