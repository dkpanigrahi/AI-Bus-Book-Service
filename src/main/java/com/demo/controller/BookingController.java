package com.demo.controller;

import com.demo.dto.ApiResponse;
import com.demo.dto.BookingDto;
import com.demo.dto.BookingGroupDto;
import com.demo.dto.SeatAvailabilityResponse;
import com.demo.dto.request.HoldSeatsRequest;
import com.demo.service.BookingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Redesigned BookingController.
 *
 * Changes vs original:
 *  - POST /hold now accepts a typed HoldSeatsRequest (@Valid) instead of Map<String,Object>
 *  - Group-level endpoints replace flat /bookings/{id} queries
 *  - Cancel operates on the group (all seats cancel together)
 *  - Seat availability endpoint unchanged (still called by BusService via Feign)
 */
@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
@Slf4j
public class BookingController {

    private final BookingService bookingService;

    // =========================================================================
    //  HOLD — Saga entry point
    // =========================================================================

    /**
     * Validates seats, computes per-seat prices, holds all seats in one
     * BookingGroup, and publishes BookingCreatedEvent to trigger payment.
     */
    @PostMapping("/hold")
    public ResponseEntity<ApiResponse<BookingGroupDto>> holdSeats(
            @Validated @RequestBody HoldSeatsRequest request) {

        log.info("POST /api/bookings/hold — userId={} busId={} date={} seats={}",
                request.getUserId(), request.getBusId(), request.getJourneyDate(),
                request.getPassengers().stream()
                        .map(p -> p.getSeatNumber().toString()).toList());

        BookingGroupDto group = bookingService.holdSeatsAndInitiateSaga(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Seats held. Please complete payment within 10 minutes.", group));
    }

    // =========================================================================
    //  BOOKING GROUP QUERIES
    // =========================================================================

    @GetMapping("/groups/{groupId}")
    public ResponseEntity<ApiResponse<BookingGroupDto>> getGroupById(@PathVariable Integer groupId) {
        log.info("GET /api/bookings/groups/{}", groupId);
        return ResponseEntity.ok(ApiResponse.success(bookingService.getGroupById(groupId)));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<List<BookingGroupDto>>> getBookingsByUser(
            @PathVariable Integer userId) {
        log.info("GET /api/bookings/user/{}", userId);
        return ResponseEntity.ok(ApiResponse.success(bookingService.getGroupsByUser(userId)));
    }

    // =========================================================================
    //  FLAT SEAT QUERIES (used by bus-side tools or admin views)
    // =========================================================================

    @GetMapping("/bus/{busId}")
    public ResponseEntity<ApiResponse<List<BookingDto>>> getBookingsByBusAndDate(
            @PathVariable Integer busId,
            @RequestParam String date) {
        log.info("GET /api/bookings/bus/{} date={}", busId, date);
        return ResponseEntity.ok(
                ApiResponse.success(bookingService.getBookingsByBusAndDate(busId, date)));
    }

    // =========================================================================
    //  CANCEL
    // =========================================================================

    /**
     * Cancels all seats in the group (user-initiated).
     * Publishes BookingCancelledEvent for refund and notification.
     */
    @DeleteMapping("/groups/{groupId}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancelBookingGroup(
            @PathVariable Integer groupId,
            @RequestParam Integer userId) {
        log.info("DELETE /api/bookings/groups/{}/cancel userId={}", groupId, userId);
        bookingService.cancelBookingGroup(groupId, userId);
        return ResponseEntity.ok(ApiResponse.success("Booking cancelled successfully", null));
    }

    // =========================================================================
    //  SEAT AVAILABILITY  (called by BusService via FeignClient)
    // =========================================================================

    @GetMapping("/seats/availability")
    public ResponseEntity<ApiResponse<SeatAvailabilityResponse>> getSeatAvailability(
            @RequestParam Integer busId,
            @RequestParam String date,
            @RequestParam Integer boardingStopSequence,
            @RequestParam Integer alightingStopSequence) {
        log.info("GET /api/bookings/seats/availability busId={} date={}", busId, date);
        return ResponseEntity.ok(
                ApiResponse.success(bookingService
                        .getSeatAvailability(busId, date,boardingStopSequence,alightingStopSequence)));
    }

    @GetMapping("/seats/booked-count")
    public ResponseEntity<ApiResponse<Integer>> getBookedSeatCount(
            @RequestParam Integer busId,
            @RequestParam String date) {
        return ResponseEntity.ok(
                ApiResponse.success(bookingService.getBookedSeatCount(busId, date)));
    }
}