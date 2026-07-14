package com.demo.enums;

/**
 * Whether a SeatTypePricing adjustmentValue is a flat rupee amount
 * or a percentage of the base stop-to-stop fare.
 */
public enum PriceAdjustmentType {
    /** adjustmentValue is in ₹ (e.g. +50, -100) */
    FLAT,
    /** adjustmentValue is a % (e.g. +10 means +10% of base fare) */
    PERCENTAGE
}