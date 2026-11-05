/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_ajalt_colormath.colormath_jvm

import com.github.ajalt.colormath.Color
import com.github.ajalt.colormath.parse
import com.github.ajalt.colormath.model.SRGB
import com.github.ajalt.colormath.transform.InterpolationMethods
import com.github.ajalt.colormath.transform.interpolate
import com.github.ajalt.colormath.transform.interpolator
import com.github.ajalt.colormath.transform.sequence
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Exercises color gradient interpolation: a dashboard builds a palette between its design colors.
 */
public class ColorGradientInterpolationTest {

    @Test
    public fun buildsPaletteForDashboardWidgets(): Unit {
        val primary = Color.parse("#0b3d91")
        val highlight = Color.parse("#f4d35e")
        val gradient = SRGB.interpolator {
            method = InterpolationMethods.linear()
            stop(primary, 0f)
            stop(highlight, 1f)
        }

        val palette = gradient.sequence(4).map { it.toSRGB().toHex() }.toList()
        val midpoint = primary.interpolate(highlight, 0.5f).toSRGB()
        val endpoint = gradient.interpolate(1.0).toSRGB()

        assertEquals(listOf("#0b3d91", "#596f80", "#a6a16f", "#f4d35e"), palette)
        assertEquals(128, midpoint.redInt)
        assertEquals(136, midpoint.greenInt)
        assertEquals(120, midpoint.blueInt)
        assertEquals("#f4d35e", endpoint.toHex())
    }
}
