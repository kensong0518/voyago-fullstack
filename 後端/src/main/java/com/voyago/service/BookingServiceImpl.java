package com.voyago.service;

import com.voyago.dto.BookingDto;
import com.voyago.dto.BookingRequest;
import com.voyago.dto.PageResult;
import com.voyago.entity.Booking;
import com.voyago.entity.Member;
import com.voyago.entity.Route;
import com.voyago.repository.BookingDao;
import com.voyago.repository.BookingRepository;
import com.voyago.repository.MemberRepository;
import com.voyago.repository.RouteRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class BookingServiceImpl implements BookingService {

    private final BookingDao bookingDao;             // 查詢（Criteria + 分頁）
    private final BookingRepository bookings;        // 寫入（內建交易）
    private final RouteRepository routes;
    private final MemberRepository members;

    public BookingServiceImpl(BookingDao bookingDao, BookingRepository bookings,
                              RouteRepository routes, MemberRepository members) {
        this.bookingDao = bookingDao;
        this.bookings = bookings;
        this.routes = routes;
        this.members = members;
    }

    @Override
    public List<BookingDto> listMine(Long memberId) {
        return bookingDao.findByMember(memberId).stream().map(BookingDto::from).toList();
    }

    @Override
    public PageResult<BookingDto> listMinePaged(Long memberId, int page, int size) {
        int p = Math.max(0, page);
        int s = size <= 0 ? 5 : size;
        long total = bookingDao.countByMember(memberId);
        int totalPages = (int) Math.ceil((double) total / s);
        List<BookingDto> content = bookingDao.findByMemberPaged(memberId, p, s)
                .stream().map(BookingDto::from).toList();
        return new PageResult<>(content, p, s, total, totalPages);
    }

    @Override
    @Transactional
    public BookingDto create(Long memberId, BookingRequest req) {
        LocalDate travelDate;
        try {
            travelDate = LocalDate.parse(req.travelDate());
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "出發日期格式錯誤（須為 YYYY-MM-DD）");
        }
        if (travelDate.isBefore(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "出發日期不能早於今天");
        }
        Member member = members.findById(memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "請先登入"));
        Route route = routes.findById(req.routeId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到此行程"));

        Booking b = new Booking();
        b.setMember(member);
        b.setRoute(route);
        b.setPeople(req.people());
        b.setTravelDate(travelDate);
        b.setTotalPrice(route.getPrice() * req.people());
        b.setStatus("CONFIRMED");
        bookings.save(b);
        return BookingDto.from(b);
    }

    @Override
    @Transactional
    public void cancel(Long memberId, Long bookingId) {
        Booking b = bookings.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到訂單"));
        if (!b.getMember().getId().equals(memberId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "無權限取消此訂單");
        }
        b.setStatus("CANCELLED");
        bookings.save(b);
    }
}
