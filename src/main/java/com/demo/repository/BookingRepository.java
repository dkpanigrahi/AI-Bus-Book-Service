package com.demo.repository;

import com.demo.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Integer> {

    List<Booking> findByUserId(Integer userId);

    List<Booking> findByBusIdAndBookingDate(Integer busId, LocalDate bookingDate);

    @Query("SELECT b FROM Booking b WHERE b.busId = :busId AND b.bookingDate = :date " +
           "AND (b.booked = true OR b.inProcess = true)")
    List<Booking> findOccupiedSeats(
            @Param("busId") Integer busId,
            @Param("date") LocalDate date);

    Optional<Booking> findByBusIdAndBookingDateAndSeatNo(Integer busId, LocalDate bookingDate, Integer seatNo);

    @Query("SELECT b FROM Booking b WHERE b.inProcess = true AND b.expirationTime < :now")
    List<Booking> findExpiredBookings(@Param("now") LocalDateTime now);

    @Query("SELECT b FROM Booking b WHERE b.busId = :busId AND b.bookingDate = :date AND b.booked = true")
    List<Booking> findConfirmedBookings(
            @Param("busId") Integer busId,
            @Param("date") LocalDate date);

    boolean existsByBusIdAndBookingDateAndSeatNoAndBookedTrue(
            Integer busId, LocalDate bookingDate, Integer seatNo);

    boolean existsByBusIdAndBookingDateAndSeatNoAndInProcessTrue(
            Integer busId, LocalDate bookingDate, Integer seatNo);

    List<Booking> findByIdIn(List<Integer> ids);
}