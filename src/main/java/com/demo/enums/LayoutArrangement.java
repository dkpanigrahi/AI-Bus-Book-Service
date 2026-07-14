package com.demo.enums;

/**
 * Left-side columns : Right-side columns per row.
 *
 *  TWO_TWO   → [W A] [A W]       — 4 seats/row   (most common AC seater)
 *  TWO_THREE → [W A] [A M W]     — 5 seats/row   (Volvo style)
 *  TWO_ONE   → [W A] [W]         — 3 seats/row   (luxury / semi-sleeper)
 *  ONE_ONE   → [W] [W]           — 2 seats/row   (premium / individual recliner)
 *
 * Sleeper buses typically use TWO_ONE per deck (2 lower + 1 upper per row).
 */
public enum LayoutArrangement {
    TWO_TWO,
    TWO_THREE,
    TWO_ONE,
    ONE_ONE
}