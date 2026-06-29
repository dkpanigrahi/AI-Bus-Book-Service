package com.demo.enums;

/**
 * Lifecycle states for both Booking and BookingGroup.
 *
 *  HELD       – seats locked, payment pending, expiresAt set
 *  CONFIRMED  – payment received, expiresAt cleared, transactionId set
 *  CANCELLED  – released (payment failed, user cancelled, or expiry cleanup)
 */
public enum BookingStatus {
    HELD,
    CONFIRMED,
    CANCELLED
}