package com.nitestand.standbyclock.util

import android.os.Build
import androidx.annotation.StringRes
import com.nitestand.standbyclock.R

/**
 * Manufacturer-specific *guidance text* only. App behaviour is identical on every device;
 * this just tells the user where their OEM moved the relevant system settings.
 */
object DeviceTips {

    @StringRes
    fun forManufacturer(manufacturer: String = Build.MANUFACTURER, brand: String = Build.BRAND): Int {
        val m = "${manufacturer.lowercase()} ${brand.lowercase()}"
        return when {
            "samsung" in m -> R.string.tip_samsung
            "oneplus" in m -> R.string.tip_oneplus
            listOf("xiaomi", "redmi", "poco").any { it in m } -> R.string.tip_xiaomi
            listOf("oppo", "realme").any { it in m } -> R.string.tip_oppo
            listOf("vivo", "iqoo").any { it in m } -> R.string.tip_vivo
            "google" in m -> R.string.tip_pixel
            "motorola" in m || "lenovo" in m -> R.string.tip_motorola
            else -> R.string.tip_generic
        }
    }
}
