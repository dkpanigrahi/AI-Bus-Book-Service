package com.demo.entity;

import com.demo.enums.BookingStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Represents a single-seat booking within a BookingGroup.
 *
 * Design mirrors BusService domain style:
 *  - Rich entity with builder pattern
 *  - Clear FK references to Bus entities by ID (cross-service boundary)
 *  - BoardingStopSequence + alightingStopSequence resolve price via Bus stops
 *  - finalPrice captured at booking time (snapshot) — independent of future Bus price changes
 *  - seatNumber + seatLabel are snapshots from Seat entity (cross-service reference)
 *  - status lifecycle: HELD → CONFIRMED → CANCELLED
 */
@Entity
@Table(
        name = "booking",
        indexes = {
                @Index(name = "idx_booking_group",       columnList = "booking_group_id"),
                @Index(name = "idx_booking_bus_date",    columnList = "bus_id, journey_date"),
                @Index(name = "idx_booking_status",      columnList = "status"),
                @Index(name = "idx_booking_seat_lookup", columnList = "bus_id, journey_date, seat_number, status")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    // ── Foreign key to the parent group (one group per payment/transaction) ──
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_group_id", nullable = false)
    private BookingGroup bookingGroup;

    // ── Cross-service references (IDs only — no JPA join across services) ────
    @Column(nullable = false)
    private Integer busId;

    @Column(nullable = false)
    private Integer userId;

    // ── Journey details ──────────────────────────────────────────────────────
    @Column(nullable = false)
    private LocalDate journeyDate;

    /**
     * Stop sequence in the Bus's stop list at which this passenger boards.
     * Used with alightingStopSequence to validate direction and recompute
     * price if needed.
     */
    @Column(nullable = false)
    private Integer boardingStopSequence;

    @Column(name = "boarding_stop_name", nullable = false)
    private String boardingStopName;   // snapshot from BusStop at booking time

    @Column(nullable = false)
    private Integer alightingStopSequence;

    @Column(name = "alighting_stop_name", nullable = false)
    private String alightingStopName;  // snapshot

    // ── Seat details (snapshot from SeatLayout at booking time) ──────────────
    @Column(nullable = false)
    private Integer seatNumber;        // physical seat number from Seat entity

    @Column(nullable = false)
    private String seatLabel;          // e.g. "L12", "U5", "R3"

    @Column(nullable = false)
    private String seatType;           // WINDOW / AISLE / MIDDLE (enum name snapshot)

    @Column(nullable = false)
    private String deckLevel;          // LOWER / UPPER (enum name snapshot)

    private boolean isSleeper;

    // ── Passenger info ───────────────────────────────────────────────────────
    @Column(nullable = false)
    private String passengerName;

    private Integer passengerAge;

    @Column(length = 1)
    private String passengerGender;    // M / F / O

    // ── Pricing (captured at booking time, immutable after CONFIRMED) ─────────
    /**
     * Final price paid for this seat on this segment.
     * = (alightingStop.priceFromOrigin - boardingStop.priceFromOrigin) + seatTypeAdjustment
     */
    @Column(nullable = false)
    private Double finalPrice;

    // ── Status lifecycle ──────────────────────────────────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private BookingStatus status = BookingStatus.HELD;

    /**
     * When a HELD booking must be confirmed or auto-released.
     * Null after CONFIRMED; set to now() on CANCELLED.
     */
    private LocalDateTime expiresAt;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    // ── Derived helpers ───────────────────────────────────────────────────────

    @Transient
    public boolean isHeld() { return status == BookingStatus.HELD; }

    @Transient
    public boolean isConfirmed() { return status == BookingStatus.CONFIRMED; }

    @Transient
    public boolean isCancelled() { return status == BookingStatus.CANCELLED; }

    @Transient
    public boolean isExpired() {
        return status == BookingStatus.HELD
                && expiresAt != null
                && LocalDateTime.now().isAfter(expiresAt);
    }
}