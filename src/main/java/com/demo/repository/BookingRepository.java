package com.demo.repository;

import com.demo.entity.Booking;
import com.demo.enums.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public interface BookingRepository extends JpaRepository<Booking, Integer> {

    /**
     * All active (HELD or CONFIRMED) bookings for a bus on a date.
     * Used for seat availability calculation.
     */
    List<Booking> findByBusIdAndJourneyDateAndStatusIn(
            Integer busId, LocalDate journeyDate, List<BookingStatus> statuses);

    /**
     * Count of occupied seats (for BusService's available seat count).
     */
    int countByBusIdAndJourneyDateAndStatusIn(
            Integer busId, LocalDate journeyDate, List<BookingStatus> statuses);

    /**
     * Seat lock check — is a specific seat already HELD or CONFIRMED?
     */
    boolean existsByBusIdAndJourneyDateAndSeatNumberAndStatusIn(
            Integer busId, LocalDate journeyDate, Integer seatNumber, List<BookingStatus> statuses);

    /**
     * Used by admin views to see all bookings for a bus+date.
     */
    List<Booking> findByBusIdAndJourneyDate(Integer busId, LocalDate journeyDate);

    @Query("""
                SELECT COUNT(b) > 0 FROM Booking b
                WHERE b.busId = :busId
                  AND b.journeyDate = :journeyDate
                  AND b.seatNumber = :seatNumber
                  AND b.status IN :statuses
                  AND b.boardingStopSequence < :alightingSeq
                  AND b.alightingStopSequence > :boardingSeq
            """)
    boolean existsConflictingBooking(
            @Param("busId") Integer busId,
            @Param("journeyDate") LocalDate journeyDate,
            @Param("seatNumber") Integer seatNumber,
            @Param("statuses") List<BookingStatus> statuses,
            @Param("boardingSeq") Integer boardingSeq,
            @Param("alightingSeq") Integer alightingSeq);


    // Fetch ALL conflicting seat numbers for a segment in one query
    // (used to build the seat availability map efficiently)
    @Query("""
                SELECT b.seatNumber FROM Booking b
                WHERE b.busId = :busId
                  AND b.journeyDate = :journeyDate
                  AND b.status IN :statuses
                  AND b.boardingStopSequence < :alightingSeq
                  AND b.alightingStopSequence > :boardingSeq
            """)
    Set<Integer> findOccupiedSeatNumbers(
            @Param("busId") Integer busId,
            @Param("journeyDate") LocalDate journeyDate,
            @Param("statuses") List<BookingStatus> statuses,
            @Param("boardingSeq") Integer boardingSeq,
            @Param("alightingSeq") Integer alightingSeq);
}