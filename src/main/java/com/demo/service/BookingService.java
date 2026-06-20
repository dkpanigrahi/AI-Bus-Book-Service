package com.demo.service;

import com.demo.client.BusServiceClient;
import com.demo.client.UserServiceClient;
import com.demo.dto.*;
import com.demo.entity.Booking;
import com.demo.events.BookingCreatedEvent;
import com.demo.repository.BookingRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class BookingService {

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private BusServiceClient busServiceClient;

    @Autowired
    private UserServiceClient userServiceClient;

    @Autowired
    private BookingSagaOrchestrator sagaOrchestrator;

    @Value("${booking.expiration.minutes:10}")
    private int expirationMinutes;

    // ─── Hold Seats & Initiate SAGA ────────────────────────────────

    /**
     * Holds seats for a booking and initiates the Saga Choreography flow.
     *
     * @return list of created (in-process) Booking IDs
     */
    @Transactional
    public List<Integer> holdSeatsAndInitiateSaga(
            Integer userId,
            Integer busId,
            LocalDate journeyDate,
            List<Integer> seatNumbers,
            List<String> passengerNames) {

        log.info("Holding {} seats for userId: {}, busId: {}, date: {}",
                seatNumbers.size(), userId, busId, journeyDate);

        // Validate user via FeignClient
        UserDto userDto = fetchUser(userId);
        // Validate bus via FeignClient
        BusDto busDto = fetchBus(busId);

        // Validate seat availability
        validateSeatsAvailable(busId, journeyDate, seatNumbers);

        if (seatNumbers.size() != passengerNames.size()) {
            throw new IllegalArgumentException("Seat numbers and passenger names count must match");
        }

        LocalDateTime expiration = LocalDateTime.now().plusMinutes(expirationMinutes);
        List<Booking> bookings = new ArrayList<>();

        for (int i = 0; i < seatNumbers.size(); i++) {
            Booking booking = new Booking();
            booking.setUserId(userId);
            booking.setBusId(busId);
            booking.setBookingDate(journeyDate);
            booking.setSeatNo(seatNumbers.get(i));
            booking.setPassengerName(passengerNames.get(i));
            booking.setBooked(false);
            booking.setInProcess(true);
            booking.setExpirationTime(expiration);
            bookings.add(booking);
        }

        List<Booking> savedBookings = bookingRepository.saveAll(bookings);
        List<Integer> bookingIds = savedBookings.stream()
                .map(Booking::getId)
                .collect(Collectors.toList());

        log.info("Seats held successfully. BookingIds: {}", bookingIds);

        // Initiate SAGA — publish BookingCreatedEvent
        BookingCreatedEvent event = BookingCreatedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .bookingId(bookingIds.get(0))
                .userId(userId)
                .userEmail(userDto.getEmail())
                .busId(busId)
                .journeyDate(journeyDate)
                .seatNumbers(seatNumbers)
                .passengerNames(passengerNames)
                .totalAmount((double) busDto.getTicketPrice() * seatNumbers.size())
                .status("PENDING")
                .build();

        sagaOrchestrator.initiateBookingSaga(event);

        return bookingIds;
    }

    // <------------------ Seat Availability -------------------->

    public SeatAvailabilityResponse getSeatAvailability(int busId, String date) {
        log.info("Fetching seat availability for busId: {}, date: {}", busId, date);

        LocalDate journeyDate = LocalDate.parse(date);
        BusDto busDto = fetchBus(busId);

        List<Booking> occupiedSeats = bookingRepository.findOccupiedSeats(busId, journeyDate);
        Set<Integer> occupiedSeatNumbers = occupiedSeats.stream()
                .map(Booking::getSeatNo)
                .collect(Collectors.toSet());

        Map<Integer, Boolean> seatMap = new LinkedHashMap<>();
        for (int i = 1; i <= busDto.getTotalSeats(); i++) {
            seatMap.put(i, !occupiedSeatNumbers.contains(i));
        }

        return SeatAvailabilityResponse.builder()
                .busId(busId)
                .date(date)
                .seatMap(seatMap)
                .totalSeats(busDto.getTotalSeats())
                .build();
    }

    public int getBookedSeatCount(int busId, String date) {
        LocalDate journeyDate = LocalDate.parse(date);
        return bookingRepository.findOccupiedSeats(busId, journeyDate).size();
    }

    // <--------------------- Booking Queries ----------------------->

    public List<Booking> getBookingsByUser(int userId) {
        log.info("Fetching bookings for userId: {}", userId);
        return bookingRepository.findByUserId(userId);
    }

    public Booking getBookingById(int id) {
        return bookingRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Booking not found with id: " + id));
    }

    public List<Booking> getBookingsByBusAndDate(int busId, String date) {
        return bookingRepository.findByBusIdAndBookingDate(busId, LocalDate.parse(date));
    }

    // ─── Cancellation ──────────────────────────────────────────────

    @Transactional
    public void cancelBooking(int bookingId, int userId) {
        log.info("Cancelling bookingId: {} for userId: {}", bookingId, userId);

        Booking booking = getBookingById(bookingId);

        if (!booking.getUserId().equals(userId)) {
            throw new RuntimeException("Unauthorized: This booking does not belong to the user");
        }

        if (!booking.isBooked() && !booking.isInProcess()) {
            throw new RuntimeException("Booking is already cancelled");
        }

        bookingRepository.delete(booking);
        log.info("Booking {} cancelled successfully", bookingId);
    }

    // ─── Scheduled: Expire stale in-process bookings ──────────────

    @Scheduled(fixedDelayString = "${booking.expiration.cleanup-interval-ms:60000}")
    @Transactional
    public void cleanupExpiredBookings() {
        List<Booking> expired = bookingRepository.findExpiredBookings(LocalDateTime.now());

        if (!expired.isEmpty()) {
            log.info("Cleaning up {} expired in-process bookings", expired.size());
            bookingRepository.deleteAll(expired);
            log.info("Expired bookings cleaned up successfully");
        }
    }

    // ─── Private Helpers ───────────────────────────────────────────

    private UserDto fetchUser(Integer userId) {
        try {
            ResponseEntity<ApiResponse<UserDto>> response = userServiceClient.getUserById(userId);
            if (response.getBody() != null && response.getBody().isSuccess()) {
                return response.getBody().getData();
            }
        } catch (Exception e) {
            log.error("Failed to fetch user for userId: {}. Error: {}", userId, e.getMessage());
        }
        throw new RuntimeException("User not found or user service unavailable for userId: " + userId);
    }

    private BusDto fetchBus(Integer busId) {
        try {
            ResponseEntity<ApiResponse<BusDto>> response = busServiceClient.getBusById(busId);
            if (response.getBody() != null && response.getBody().isSuccess()) {
                return response.getBody().getData();
            }
        } catch (Exception e) {
            log.error("Failed to fetch bus for busId: {}. Error: {}", busId, e.getMessage());
        }
        throw new RuntimeException("Bus not found or bus service unavailable for busId: " + busId);
    }

    private void validateSeatsAvailable(Integer busId, LocalDate journeyDate, List<Integer> seatNumbers) {
        for (Integer seatNo : seatNumbers) {
            boolean alreadyBooked = bookingRepository
                    .existsByBusIdAndBookingDateAndSeatNoAndBookedTrue(busId, journeyDate, seatNo);
            boolean inProcess = bookingRepository
                    .existsByBusIdAndBookingDateAndSeatNoAndInProcessTrue(busId, journeyDate, seatNo);

            if (alreadyBooked) {
                log.warn("Seat {} on busId: {} for date: {} is already booked", seatNo, busId, journeyDate);
                throw new RuntimeException("Seat " + seatNo + " is already booked");
            }
            if (inProcess) {
                log.warn("Seat {} on busId: {} for date: {} is currently being processed", seatNo, busId, journeyDate);
                throw new RuntimeException("Seat " + seatNo + " is currently being held. Please try again shortly.");
            }
        }
    }

    public BookingDto mapToDto(Booking booking) {
        return BookingDto.builder()
                .id(booking.getId())
                .seatNo(booking.getSeatNo())
                .passengerName(booking.getPassengerName())
                .bookingDate(booking.getBookingDate())
                .booked(booking.isBooked())
                .inProcess(booking.isInProcess())
                .expirationTime(booking.getExpirationTime())
                .build();
    }
}