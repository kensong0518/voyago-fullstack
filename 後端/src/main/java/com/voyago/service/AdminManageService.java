package com.voyago.service;

import com.voyago.dto.AdminBookingDto;
import com.voyago.dto.PageResult;
import com.voyago.dto.RouteDto;
import com.voyago.dto.RouteUpsertRequest;

/** 後台資料管理：行程上架 / 編輯 / 刪除，以及檢視與調整所有會員的訂單。只開給 STAFF。 */
public interface AdminManageService {
    RouteDto createRoute(RouteUpsertRequest req);
    RouteDto updateRoute(Long id, RouteUpsertRequest req);
    void deleteRoute(Long id);

    PageResult<AdminBookingDto> listBookings(String status, int page, int size);
    AdminBookingDto updateBookingStatus(Long id, String status);
}
