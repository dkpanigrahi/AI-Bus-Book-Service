package com.demo.entity;

import com.demo.enums.BookingStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A BookingGroup represents one logical booking session:
 * one user, one bus, one journey date, N passengers (seats).
 *
 * This maps closely to how BusService handles its BusStop list —
 * a parent aggregate that owns a list of child records.
 *
 * A single PaymentCreatedEvent / PaymentCompletedEvent operates on
 * the group as a whole; the Saga orchestrator confirms or cancels
 * all child Booking records together.
 *
 * Lifecycle:
 *   HELD → (payment success) → CONFIRMED
 *   HELD → (payment failure / expiry) → CANCELLED
 *   CONFIRMED → (user cancels) → CANCELLED
 */
@Entity
@Table(
    name = "booking_group",
    indexes = {
        @Index(name = "idx_bg_user",       columnList = "user_id"),
        @Index(name = "idx_bg_bus_date",   columnList = "bus_id, journey_date"),
        @Index(name = "idx_bg_status",     columnList = "status"),
        @Index(name = "idx_bg_event",      columnList = "saga_event_id")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /**
     * Unique identifier for the Saga event chain.
     * Published in BookingCreatedEvent.eventId; used to correlate
     * PaymentCompletedEvent back to this group.
     */
    @Column(nullable = false, unique = true)
    private String sagaEventId;

    // ── Cross-service references ──────────────────────────────────────────────
    @Column(nullable = false)
    private Integer userId;

    @Column(nullable = false)
    private String userEmail;          // snapshot — avoids feign call at notification time

    @Column(nullable = false)
    private Integer busId;

    @Column(nullable = false)
    private String busNo;              // snapshot

    // ── Journey details ───────────────────────────────────────────────────────
    @Column(nullable = false)
    private LocalDate journeyDate;

    @Column(nullable = false)
    private Integer boardingStopSequence;

    @Column(nullable = false)
    private String boardingStopName;

    @Column(nullable = false)
    private Integer alightingStopSequence;

    @Column(nullable = false)
    private String alightingStopName;

    // ── Child bookings (one per seat / passenger) ─────────────────────────────
    @OneToMany(mappedBy = "bookingGroup", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Booking> bookings = new ArrayList<>();

    // ── Financials ────────────────────────────────────────────────────────────
    /** Sum of all child Booking.finalPrice values, captured at hold time. */
    @Column(nullable = false)
    private Double totalAmount;

    /** Payment gateway transaction ID, populated after PaymentCompletedEvent. */
    private String transactionId;

    // ── Status ────────────────────────────────────────────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private BookingStatus status = BookingStatus.HELD;

    private LocalDateTime expiresAt;   // propagated from Booking children

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    // ── Helpers ───────────────────────────────────────────────────────────────

    public void addBooking(Booking booking) {
        booking.setBookingGroup(this);
        this.bookings.add(booking);
    }

    @Transient
    public List<Integer> getSeatNumbers() {
        return bookings.stream().map(Booking::getSeatNumber).toList();
    }

    @Transient
    public List<String> getPassengerNames() {
        return bookings.stream().map(Booking::getPassengerName).toList();
    }

    @Transient
    public boolean isExpired() {
        return status == BookingStatus.HELD
                && expiresAt != null
                && LocalDateTime.now().isAfter(expiresAt);
    }
}