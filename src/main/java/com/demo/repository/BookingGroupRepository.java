package com.demo.repository;

import com.demo.entity.BookingGroup;
import com.demo.enums.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BookingGroupRepository extends JpaRepository<BookingGroup, Integer> {

    /** Resolve a group by its Saga correlation ID. */
    Optional<BookingGroup> findBySagaEventId(String sagaEventId);

    /** All booking groups for a user, newest first. */
    List<BookingGroup> findByUserIdOrderByCreatedAtDesc(Integer userId);

    /** Expired HELD groups — for scheduled cleanup. */
    List<BookingGroup> findByStatusAndExpiresAtBefore(BookingStatus status, LocalDateTime now);

    /** Admin / bus operator view. */
    List<BookingGroup> findByBusIdAndJourneyDate(Integer busId, java.time.LocalDate journeyDate);
}