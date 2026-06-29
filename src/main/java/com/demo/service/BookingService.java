package com.demo.service;

import com.demo.client.BusServiceClient;
import com.demo.client.UserServiceClient;
import com.demo.dto.*;
import com.demo.dto.request.HoldSeatsRequest;
import com.demo.entity.Booking;
import com.demo.entity.BookingGroup;
import com.demo.enums.BookingStatus;
import com.demo.events.BookingCreatedEvent;
import com.demo.exception.SeatUnavailableException;
import com.demo.kafka.BookingEventPublisher;
import com.demo.repository.BookingGroupRepository;
import com.demo.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.errors.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Redesigned BookingService.
 *
 * Key changes vs original:
 *
 *  1. Uses BookingGroup (aggregate) + Booking (per-seat) entities instead of
 *     a flat Booking that maps one record per seat with no parent link.
 *
 *  2. Price is computed per-seat using stop sequences (boardingStop → alightingStop)
 *     and the BusDto.stops price map, matching BusService.calculateTicketPrice logic.
 *     No more flat busDto.ticketPrice * count.
 *
 *  3. Seat snapshot data (seatLabel, seatType, deckLevel) is captured at hold time
 *     from the SeatAvailabilityResponse's detailed seat list, so the booking record
 *     is self-contained.
 *
 *  4. Seat lock check uses BookingStatus (HELD/CONFIRMED) rather than two boolean
 *     columns (booked/inProcess), consistent with the enum lifecycle on the entity.
 *
 *  5. BookingCreatedEvent carries full per-seat detail + segment info for Payment
 *     Service and Notification Service.
 *
 *  6. Saga orchestrator now resolves the group by sagaEventId, not by individual IDs.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BookingService {

    private final BookingGroupRepository bookingGroupRepository;
    private final BookingRepository bookingRepository;
    private final BusServiceClient busServiceClient;
    private final UserServiceClient userServiceClient;
    private final BookingSagaOrchestrator sagaOrchestrator;
    private final BookingEventPublisher eventPublisher;

    @Value("${booking.expiration.minutes:10}")
    private int expirationMinutes;

    // =========================================================================
    //  HOLD SEATS  (Saga Step 1)
    // =========================================================================

    /**
     * Validates the user, bus, stop segment, and seat availability,
     * computes per-seat prices, persists one BookingGroup + N Booking records,
     * then publishes BookingCreatedEvent to kick off the payment Saga.
     *
     * @return the persisted BookingGroupDto (with all child bookings)
     */
    @Transactional
    public BookingGroupDto holdSeatsAndInitiateSaga(HoldSeatsRequest req) {
        log.info("Hold request — userId={} busId={} date={} seats={}",
                req.getUserId(), req.getBusId(), req.getJourneyDate(),
                req.getPassengers().stream().map(HoldSeatsRequest.PassengerSeatRequest::getSeatNumber).toList());

        // ── 1. Validate user & bus via FeignClient ────────────────────────────
        UserDto userDto = fetchUser(req.getUserId());
        BusDetailDto busDto = fetchBusDetail(req.getBusId()); // includes stops + seat layout

        // ── 2. Validate stop segment direction ────────────────────────────────
        if (req.getBoardingStopSequence() >= req.getAlightingStopSequence()) {
            throw new IllegalArgumentException(
                    "Boarding stop sequence must be less than alighting stop sequence");
        }

        BusDetailDto.StopDto boardingStop = resolveStop(busDto, req.getBoardingStopSequence());
        BusDetailDto.StopDto alightingStop = resolveStop(busDto, req.getAlightingStopSequence());

        // ── 3. Validate seats are available ───────────────────────────────────
        List<Integer> requestedSeats = req.getPassengers().stream()
                .map(HoldSeatsRequest.PassengerSeatRequest::getSeatNumber)
                .toList();
        validateSeatsAvailable(req.getBusId(), req.getJourneyDate(), requestedSeats);

        // ── 4. Snapshot seat metadata from bus seat layout ────────────────────
        Map<Integer, BusDetailDto.SeatDto> seatMetaMap = buildSeatMetaMap(busDto, requestedSeats);

        // ── 5. Compute per-seat prices ────────────────────────────────────────
        int baseSegmentPrice = alightingStop.getPriceFromOrigin() - boardingStop.getPriceFromOrigin();

        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(expirationMinutes);
        String sagaEventId = UUID.randomUUID().toString();

        // ── 6. Build BookingGroup ─────────────────────────────────────────────
        BookingGroup group = BookingGroup.builder()
                .sagaEventId(sagaEventId)
                .userId(req.getUserId())
                .userEmail(userDto.getEmail())
                .busId(req.getBusId())
                .busNo(busDto.getBusNo())
                .journeyDate(req.getJourneyDate())
                .boardingStopSequence(req.getBoardingStopSequence())
                .boardingStopName(boardingStop.getStopName())
                .alightingStopSequence(req.getAlightingStopSequence())
                .alightingStopName(alightingStop.getStopName())
                .status(BookingStatus.HELD)
                .expiresAt(expiresAt)
                .build();

        // ── 7. Build child Booking records ────────────────────────────────────
        double totalAmount = 0.0;
        List<BookingCreatedEvent.SeatBookingDetail> seatDetails = new ArrayList<>();

        for (HoldSeatsRequest.PassengerSeatRequest p : req.getPassengers()) {
            BusDetailDto.SeatDto seatMeta = seatMetaMap.get(p.getSeatNumber());

            // Per-seat price = base segment price + seat-type adjustment
            double seatPrice = computeSeatPrice(baseSegmentPrice, seatMeta, busDto);
            totalAmount += seatPrice;

            Booking booking = Booking.builder()
                    .busId(req.getBusId())
                    .userId(req.getUserId())
                    .journeyDate(req.getJourneyDate())
                    .boardingStopSequence(req.getBoardingStopSequence())
                    .boardingStopName(boardingStop.getStopName())
                    .alightingStopSequence(req.getAlightingStopSequence())
                    .alightingStopName(alightingStop.getStopName())
                    .seatNumber(p.getSeatNumber())
                    .seatLabel(seatMeta.getSeatLabel())
                    .seatType(seatMeta.getSeatType())
                    .deckLevel(seatMeta.getDeckLevel())
                    .isSleeper(seatMeta.isSleeper())
                    .passengerName(p.getPassengerName())
                    .passengerAge(p.getPassengerAge())
                    .passengerGender(p.getPassengerGender())
                    .finalPrice(seatPrice)
                    .status(BookingStatus.HELD)
                    .expiresAt(expiresAt)
                    .build();

            group.addBooking(booking);

            seatDetails.add(BookingCreatedEvent.SeatBookingDetail.builder()
                    .seatNumber(p.getSeatNumber())
                    .seatLabel(seatMeta.getSeatLabel())
                    .seatType(seatMeta.getSeatType())
                    .deckLevel(seatMeta.getDeckLevel())
                    .passengerName(p.getPassengerName())
                    .passengerAge(p.getPassengerAge())
                    .passengerGender(p.getPassengerGender())
                    .price(seatPrice)
                    .build());
        }

        group.setTotalAmount(totalAmount);
        BookingGroup saved = bookingGroupRepository.save(group);

        // Backfill bookingId into seatDetails after save (IDs now assigned)
        List<Booking> savedBookings = saved.getBookings();
        for (int i = 0; i < savedBookings.size(); i++) {
            seatDetails.get(i).setBookingId(savedBookings.get(i).getId());
        }

        log.info("BookingGroup id={} held. sagaEventId={} seats={} total={}",
                saved.getId(), sagaEventId, requestedSeats.size(), totalAmount);

        // ── 8. Publish BookingCreatedEvent → triggers Payment Service ─────────
        BookingCreatedEvent event = BookingCreatedEvent.builder()
                .eventId(sagaEventId)
                .bookingGroupId(saved.getId())
                .userId(req.getUserId())
                .userEmail(userDto.getEmail())
                .busId(req.getBusId())
                .busNo(busDto.getBusNo())
                .journeyDate(req.getJourneyDate())
                .boardingStopSequence(req.getBoardingStopSequence())
                .boardingStopName(boardingStop.getStopName())
                .alightingStopSequence(req.getAlightingStopSequence())
                .alightingStopName(alightingStop.getStopName())
                .seats(seatDetails)
                .totalAmount(totalAmount)
                .status("PENDING")
                .build();

        sagaOrchestrator.initiateBookingSaga(event);

        return mapGroupToDto(saved);
    }

    // =========================================================================
    //  QUERIES
    // =========================================================================

    @Transactional(readOnly = true)
    public BookingGroupDto getGroupById(Integer groupId) {
        return mapGroupToDto(findGroupById(groupId));
    }

    @Transactional(readOnly = true)
    public List<BookingGroupDto> getGroupsByUser(Integer userId) {
        return bookingGroupRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream().map(this::mapGroupToDto).toList();
    }

    @Transactional(readOnly = true)
    public List<BookingDto> getBookingsByBusAndDate(Integer busId, String date) {
        return bookingRepository
                .findByBusIdAndJourneyDateAndStatusIn(busId, LocalDate.parse(date),
                        List.of(BookingStatus.HELD, BookingStatus.CONFIRMED))
                .stream().map(this::mapBookingToDto).toList();
    }

    // =========================================================================
    //  SEAT AVAILABILITY  (called by BusService via FeignClient)
    // =========================================================================

    /**
     * Returns a map of seatNumber → available (true/false).
     * A seat is unavailable when a HELD or CONFIRMED booking exists for it.
     */
    public SeatAvailabilityResponse getSeatAvailability(
            Integer busId, String date,
            Integer boardingStopSeq, Integer alightingStopSeq) {

        LocalDate journeyDate = LocalDate.parse(date);
        BusDetailDto busDto = fetchBusDetail(busId);

        Set<Integer> occupiedSeats = bookingRepository.findOccupiedSeatNumbers(
                busId, journeyDate,
                List.of(BookingStatus.HELD, BookingStatus.CONFIRMED),
                boardingStopSeq, alightingStopSeq);

        Map<Integer, Boolean> seatMap = new LinkedHashMap<>();
        for (int i = 1; i <= busDto.getTotalSeats(); i++) {
            seatMap.put(i, !occupiedSeats.contains(i));
        }

        return SeatAvailabilityResponse.builder()
                .busId(busId).date(date)
                .boardingStopSequence(boardingStopSeq)
                .alightingStopSequence(alightingStopSeq)
                .seatMap(seatMap)
                .totalSeats(busDto.getTotalSeats())
                .availableSeats(busDto.getTotalSeats() - occupiedSeats.size())
                .build();
    }

    @Transactional(readOnly = true)
    public Integer getBookedSeatCount(Integer busId, String date) {
        LocalDate journeyDate = LocalDate.parse(date);
        return bookingRepository.countByBusIdAndJourneyDateAndStatusIn(
                busId, journeyDate, List.of(BookingStatus.HELD, BookingStatus.CONFIRMED));
    }

    // =========================================================================
    //  CANCELLATION
    // =========================================================================

    /**
     * User-initiated cancellation of a confirmed or held group.
     * All child bookings are marked CANCELLED.
     * Publishes BookingCancelledEvent for downstream cleanup (refund, notification).
     */
    @Transactional
    public void cancelBookingGroup(Integer groupId, Integer userId) {
        log.info("Cancel request groupId={} userId={}", groupId, userId);
        BookingGroup group = findGroupById(groupId);

        if (!group.getUserId().equals(userId)) {
            throw new RuntimeException("Unauthorized: booking group does not belong to user " + userId);
        }
        if (group.getStatus() == BookingStatus.CANCELLED) {
            throw new RuntimeException("Booking group is already cancelled");
        }

        group.setStatus(BookingStatus.CANCELLED);
        group.getBookings().forEach(b -> b.setStatus(BookingStatus.CANCELLED));
        bookingGroupRepository.save(group);

        log.info("BookingGroup id={} cancelled", groupId);
        eventPublisher.publishBookingCancelledEvent(buildCancelledEvent(group, "User cancelled"));
    }

    // =========================================================================
    //  SCHEDULED — Expire stale HELD bookings
    // =========================================================================

    @Scheduled(fixedDelayString = "${booking.expiration.cleanup-interval-ms:60000}")
    @Transactional
    public void cleanupExpiredBookings() {
        List<BookingGroup> expired = bookingGroupRepository
                .findByStatusAndExpiresAtBefore(BookingStatus.HELD, LocalDateTime.now());

        if (expired.isEmpty()) return;

        log.info("Expiring {} stale HELD booking groups", expired.size());
        expired.forEach(group -> {
            group.setStatus(BookingStatus.CANCELLED);
            group.getBookings().forEach(b -> b.setStatus(BookingStatus.CANCELLED));
            eventPublisher.publishBookingCancelledEvent(buildCancelledEvent(group, "Booking expired"));
        });
        bookingGroupRepository.saveAll(expired);
        log.info("Expired group cleanup complete");
    }

    // =========================================================================
    //  PRIVATE — SAGA SUPPORT (called by BookingSagaOrchestrator)
    // =========================================================================

    @Transactional
    public BookingGroup confirmGroup(String sagaEventId, String transactionId) {
        BookingGroup group = findGroupBySagaEventId(sagaEventId);
        group.setStatus(BookingStatus.CONFIRMED);
        group.setTransactionId(transactionId);
        group.setExpiresAt(null);
        group.getBookings().forEach(b -> {
            b.setStatus(BookingStatus.CONFIRMED);
            b.setExpiresAt(null);
        });
        return bookingGroupRepository.save(group);
    }

    @Transactional
    public BookingGroup cancelGroupByEventId(String sagaEventId, String reason) {
        BookingGroup group = findGroupBySagaEventId(sagaEventId);
        group.setStatus(BookingStatus.CANCELLED);
        group.getBookings().forEach(b -> b.setStatus(BookingStatus.CANCELLED));
        bookingGroupRepository.save(group);
        log.info("BookingGroup sagaEventId={} cancelled: {}", sagaEventId, reason);
        return group;
    }

    public BookingGroup findGroupBySagaEventId(String sagaEventId) {
        return bookingGroupRepository.findBySagaEventId(sagaEventId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "BookingGroup"+ "sagaEventId"+ sagaEventId));
    }

    // =========================================================================
    //  PRIVATE — HELPERS
    // =========================================================================

    private BookingGroup findGroupById(Integer id) {
        return bookingGroupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("BookingGroup"+ "id"+ id));
    }

    private UserDto fetchUser(Integer userId) {
        try {
            ResponseEntity<ApiResponse<UserDto>> resp = userServiceClient.getUserById(userId);
            if (resp.getBody() != null && resp.getBody().isSuccess()) return resp.getBody().getData();
        } catch (Exception e) {
            log.error("User service error for userId={}: {}", userId, e.getMessage());
        }
        throw new RuntimeException("User not found or user service unavailable. userId=" + userId);
    }

    private BusDetailDto fetchBusDetail(Integer busId) {
        try {
            ResponseEntity<ApiResponse<BusDetailDto>> resp = busServiceClient.getBusById(busId);
            if (resp.getBody() != null && resp.getBody().isSuccess()) return resp.getBody().getData();
        } catch (Exception e) {
            log.error("Bus service error for busId={}: {}", busId, e.getMessage());
        }
        throw new RuntimeException("Bus not found or bus service unavailable. busId=" + busId);
    }

    private BusDetailDto.StopDto resolveStop(BusDetailDto busDto, int stopSequence) {
        return busDto.getStops().stream()
                .filter(s -> s.getStopSequence() == stopSequence)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Stop sequence " + stopSequence + " not found on busId=" + busDto.getId()));
    }

    private Map<Integer, BusDetailDto.SeatDto> buildSeatMetaMap(
            BusDetailDto busDto, List<Integer> requestedSeats) {
        Map<Integer, BusDetailDto.SeatDto> map = busDto.getSeatLayout().getSeats().stream()
                .filter(s -> requestedSeats.contains(s.getSeatNumber()))
                .collect(Collectors.toMap(BusDetailDto.SeatDto::getSeatNumber, s -> s));

        requestedSeats.forEach(seatNo -> {
            if (!map.containsKey(seatNo)) {
                throw new IllegalArgumentException(
                        "Seat number " + seatNo + " does not exist on busId=" + busDto.getId());
            }
            if (!map.get(seatNo).isActive()) {
                throw new SeatUnavailableException("Seat " + seatNo + " is disabled on this bus");
            }
        });
        return map;
    }

    /**
     * Computes per-seat final price.
     * = base segment price + flat/percentage seat-type adjustment (if configured).
     */
    private double computeSeatPrice(int baseSegmentPrice, BusDetailDto.SeatDto seatMeta, BusDetailDto busDto) {
        // Find matching pricing rule for this seat type + deck level
        Optional<BusDetailDto.SeatTypePricingDto> pricingOpt = busDto.getSeatTypePricings().stream()
                .filter(p -> p.getSeatType().equals(seatMeta.getSeatType())
                        && p.getDeckLevel().equals(seatMeta.getDeckLevel()))
                .findFirst();

        if (pricingOpt.isEmpty()) return baseSegmentPrice;

        BusDetailDto.SeatTypePricingDto pricing = pricingOpt.get();
        return switch (pricing.getAdjustmentType()) {
            case "FLAT"       -> baseSegmentPrice + pricing.getAdjustmentValue();
            case "PERCENTAGE" -> baseSegmentPrice + (baseSegmentPrice * pricing.getAdjustmentValue() / 100.0);
            default           -> baseSegmentPrice;
        };
    }

    private void validateSeatsAvailable(Integer busId, LocalDate journeyDate, List<Integer> seatNumbers) {
        for (Integer seatNo : seatNumbers) {
            boolean occupied = bookingRepository
                    .existsByBusIdAndJourneyDateAndSeatNumberAndStatusIn(
                            busId, journeyDate, seatNo,
                            List.of(BookingStatus.HELD, BookingStatus.CONFIRMED));
            if (occupied) {
                log.warn("Seat {} on busId={} date={} is unavailable", seatNo, busId, journeyDate);
                throw new SeatUnavailableException("Seat " + seatNo + " is not available");
            }
        }
    }

    private com.demo.events.BookingCancelledEvent buildCancelledEvent(BookingGroup group, String reason) {
        return com.demo.events.BookingCancelledEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .sagaEventId(group.getSagaEventId())
                .bookingGroupId(group.getId())
                .bookingIds(group.getBookings().stream().map(Booking::getId).toList())
                .userId(group.getUserId())
                .userEmail(group.getUserEmail())
                .reason(reason)
                .build();
    }

    // =========================================================================
    //  MAPPING
    // =========================================================================

    public BookingGroupDto mapGroupToDto(BookingGroup group) {
        return BookingGroupDto.builder()
                .id(group.getId())
                .sagaEventId(group.getSagaEventId())
                .userId(group.getUserId())
                .userEmail(group.getUserEmail())
                .busId(group.getBusId())
                .busNo(group.getBusNo())
                .journeyDate(group.getJourneyDate())
                .boardingStopSequence(group.getBoardingStopSequence())
                .boardingStopName(group.getBoardingStopName())
                .alightingStopSequence(group.getAlightingStopSequence())
                .alightingStopName(group.getAlightingStopName())
                .totalAmount(group.getTotalAmount())
                .transactionId(group.getTransactionId())
                .status(group.getStatus())
                .expiresAt(group.getExpiresAt())
                .createdAt(group.getCreatedAt())
                .bookings(group.getBookings().stream().map(this::mapBookingToDto).toList())
                .build();
    }

    public BookingDto mapBookingToDto(Booking b) {
        return BookingDto.builder()
                .id(b.getId())
                .bookingGroupId(b.getBookingGroup().getId())
                .busId(b.getBusId())
                .userId(b.getUserId())
                .journeyDate(b.getJourneyDate())
                .boardingStopName(b.getBoardingStopName())
                .alightingStopName(b.getAlightingStopName())
                .seatNumber(b.getSeatNumber())
                .seatLabel(b.getSeatLabel())
                .seatType(b.getSeatType())
                .deckLevel(b.getDeckLevel())
                .isSleeper(b.isSleeper())
                .passengerName(b.getPassengerName())
                .passengerAge(b.getPassengerAge())
                .passengerGender(b.getPassengerGender())
                .finalPrice(b.getFinalPrice())
                .status(b.getStatus())
                .expiresAt(b.getExpiresAt())
                .createdAt(b.getCreatedAt())
                .build();
    }
}