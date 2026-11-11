package com.nitestand.standbyclock.ui.clock

/**
 * Pairs the glyphs of an old and a new time string for the minute-change animation. Strings are
 * aligned from the right (minutes stay put when "9:59" becomes "10:00"). Each pair says whether the
 * glyph is unchanged (it only glides to its new position) or changed (old fades out, new fades in).
 */
object GlyphTransition {
    data class Pair(val oldIndex: Int?, val newIndex: Int?, val unchanged: Boolean)

    fun pair(old: List<String>, new: List<String>): List<Pair> {
        val n = maxOf(old.size, new.size)
        return (1..n).map { fromEnd ->
            val oi = (old.size - fromEnd).takeIf { it >= 0 }
            val ni = (new.size - fromEnd).takeIf { it >= 0 }
            Pair(oi, ni, oi != null && ni != null && old[oi] == new[ni])
        }
    }
}
