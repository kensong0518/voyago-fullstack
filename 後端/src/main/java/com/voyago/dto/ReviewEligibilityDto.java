package com.voyago.dto;

/** 目前登入會員能不能評價這個行程；已寫過的話一併帶回，前端直接進入編輯。 */
public record ReviewEligibilityDto(boolean canReview, String reason, Integer myRating, String myContent) {}
