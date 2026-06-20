package com.demo.controller;

import com.demo.dto.ApiResponse;
import com.demo.dto.BookingDto;
import com.demo.dto.SeatAvailabilityResponse;
import com.demo.entity.Booking;
import com.demo.service.BookingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/bookings")
@Slf4j
public class BookingController {

    @Autowired
    private BookingService bookingService;

    /**
     * Hold seats and initiate the Saga booking flow.
     * Body: { userId, busId, journeyDate, seatNumbers, passengerNames }
     */
    @PostMapping("/hold")
    public ResponseEntity<ApiResponse<Map<String, Object>>> holdSeats(
            @RequestBody Map<String, Object> request,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId) {

        Integer userId = Integer.valueOf(request.get("userId").toString());
        Integer busId = Integer.valueOf(request.get("busId").toString());
        String journeyDateStr = request.get("journeyDate").toString();
        LocalDate journeyDate = LocalDate.parse(journeyDateStr);

        @SuppressWarnings("unchecked")
        List<Integer> seatNumbers = ((List<Object>) request.get("seatNumbers"))
                .stream().map(o -> Integer.valueOf(o.toString())).collect(Collectors.toList());

        @SuppressWarnings("unchecked")
        List<String> passengerNames = (List<String>) request.get("passengerNames");

        log.info("Hold seats request - userId: {}, busId: {}, date: {}, seats: {}",
                userId, busId, journeyDate, seatNumbers);

        List<Integer> bookingIds = bookingService.holdSeatsAndInitiateSaga(
                userId, busId, journeyDate, seatNumbers, passengerNames);

        Map<String, Object> response = Map.of(
                "bookingIds", bookingIds,
                "status", "SEATS_HELD",
                "message", "Seats held successfully. Complete payment to confirm.",
                "expiresInMinutes", 10
        );

        return ResponseEntity.ok(ApiResponse.success("Seats held. Please complete payment.", response));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<List<BookingDto>>> getBookingsByUser(@PathVariable int userId) {
        log.info("Fetching bookings for userId: {}", userId);
        List<BookingDto> bookings = bookingService.getBookingsByUser(userId).stream()
                .map(bookingService::mapToDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(bookings));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BookingDto>> getBookingById(@PathVariable int id) {
        log.info("Fetching booking by id: {}", id);
        Booking booking = bookingService.getBookingById(id);
        return ResponseEntity.ok(ApiResponse.success(bookingService.mapToDto(booking)));
    }

    @GetMapping("/bus/{busId}")
    public ResponseEntity<ApiResponse<List<BookingDto>>> getBookingsByBusAndDate(
            @PathVariable int busId,
            @RequestParam String date) {
        log.info("Fetching bookings for busId: {}, date: {}", busId, date);
        List<BookingDto> bookings = bookingService.getBookingsByBusAndDate(busId, date).stream()
                .map(bookingService::mapToDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(bookings));
    }

    @DeleteMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancelBooking(
            @PathVariable int id,
            @RequestParam int userId) {
        log.info("Cancel request for bookingId: {}, userId: {}", id, userId);
        bookingService.cancelBooking(id, userId);
        return ResponseEntity.ok(ApiResponse.success("Booking cancelled successfully", null));
    }

    // ─── Seat Availability (used by Bus Service via FeignClient) ──

    @GetMapping("/seats/availability")
    public ResponseEntity<ApiResponse<SeatAvailabilityResponse>> getSeatAvailability(
            @RequestParam int busId,
            @RequestParam String date) {
        log.info("Seat availability request - busId: {}, date: {}", busId, date);
        SeatAvailabilityResponse availability = bookingService.getSeatAvailability(busId, date);
        return ResponseEntity.ok(ApiResponse.success(availability));
    }

    @GetMapping("/seats/booked-count")
    public ResponseEntity<ApiResponse<Integer>> getBookedSeatCount(
            @RequestParam int busId,
            @RequestParam String date) {
        int count = bookingService.getBookedSeatCount(busId, date);
        return ResponseEntity.ok(ApiResponse.success(count));
    }
}