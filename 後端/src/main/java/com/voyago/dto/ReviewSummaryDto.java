package com.voyago.dto;

import java.util.List;

/** 行程頁評價區：平均分數、總數、1~5 星各幾則（distribution[0] 是 1 星），以及最新評價。 */
public record ReviewSummaryDto(double average, int count, List<Integer> distribution, List<ReviewDto> items) {}
