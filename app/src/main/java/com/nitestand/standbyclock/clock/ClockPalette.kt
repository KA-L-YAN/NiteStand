package com.nitestand.standbyclock.clock

/**
 * Reusable clock colour system. Colours are ARGB longs so the domain stays free of UI types.
 *
 * - [primary]: main digits / hands
 * - [accent]: details such as the date, second hand or progress dot
 * - [gradientStart]/[gradientEnd]: for expressive faces (Color, Bubble)
 */
enum class ClockPalette(
    val primary: Long,
    val accent: Long,
    val gradientStart: Long,
    val gradientEnd: Long,
) {
    WHITE(0xFFF5F5F7, 0xFFFF9F0A, 0xFFFFFFFF, 0xFFC7C7CC),
    WARM_WHITE(0xFFFFF0DC, 0xFFFFB25B, 0xFFFFF4E6, 0xFFFFCF99),
    SOFT_ORANGE(0xFFFFB067, 0xFFFFD08A, 0xFFFF8A4C, 0xFFFFC36B),
    YELLOW(0xFFFFD866, 0xFFFFF0A8, 0xFFFFE37A, 0xFFFFA94D),
    GREEN(0xFF7EE2A4, 0xFFB6F2C9, 0xFF4EDDA0, 0xFF6FC3FF),
    BLUE(0xFF82B9FF, 0xFFB8D6FF, 0xFF5AA9FF, 0xFFA58BFF),
    PURPLE(0xFFBBA4FF, 0xFFDCCBFF, 0xFF9C7BFF, 0xFFFF8FBF),
    PINK(0xFFFF9EC9, 0xFFFFCCE1, 0xFFFF7EB0, 0xFFFFB36B),
    RED(0xFFFF6F61, 0xFFFFA59B, 0xFFFF5A52, 0xFFFF9F5A),
    ;

    /** Palettes that are essentially neutral (analog faces keep white hands with a colour accent otherwise). */
    val isNeutral: Boolean get() = this == WHITE || this == WARM_WHITE
}
