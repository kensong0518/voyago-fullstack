package com.voyago.dto;

import com.voyago.entity.Route;

public record RouteDto(
    Long id, String slug, String name, String summary, String description,
    String location, String country, Integer days, Integer price,
    Double rating, Integer reviews, String imageUrl, String tags, Boolean featured
) {
    public static RouteDto from(Route r) {
        return new RouteDto(r.getId(), r.getSlug(), r.getName(), r.getSummary(), r.getDescription(),
            r.getLocation(), r.getCountry(), r.getDays(), r.getPrice(), r.getRating(),
            r.getReviews(), r.getImageUrl(), r.getTags(), r.getFeatured());
    }
}
