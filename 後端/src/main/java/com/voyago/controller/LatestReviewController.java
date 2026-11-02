package com.voyago.controller;

import com.voyago.dto.ReviewDto;
import com.voyago.service.ReviewService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 首頁用：全站最新的真實好評（沒有評價時回空陣列，前端整區不顯示）。 */
@RestController
public class LatestReviewController {

    private final ReviewService reviews;

    public LatestReviewController(ReviewService reviews) {
        this.reviews = reviews;
    }

    @GetMapping("/api/reviews/latest")
    public List<ReviewDto> latest(@RequestParam(defaultValue = "3") int limit) {
        return reviews.latest(limit);
    }
}
