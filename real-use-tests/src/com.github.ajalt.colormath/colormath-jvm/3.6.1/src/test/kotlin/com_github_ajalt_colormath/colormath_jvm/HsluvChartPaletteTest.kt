/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_ajalt_colormath.colormath_jvm

import com.github.ajalt.colormath.model.HSLuv
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Exercises HSLuv color modeling: a chart renderer creates a perceptually spaced palette for its data series.
 */
public class HsluvChartPaletteTest {

    @Test
    public fun rendersPerceptuallySpacedSeriesPalette(): Unit {
        val palette = listOf(
            SeriesColor("orders", HSLuv(20f, 75f, 65f)),
            SeriesColor("returns", HSLuv(140f, 75f, 65f)),
            SeriesColor("forecast", HSLuv(260f, 75f, 65f)),
        )

        val cssColors = palette.associate { it.name to it.color.toSRGB().toHex() }
        val restoredColors = palette.map { it.color.toSRGB().toHSLuv() }

        assertEquals(
            mapOf("orders" to "#ed7e69", "returns" to "#4fb075", "forecast" to "#899ae7"),
            cssColors,
        )
        assertEquals(listOf(20f, 140f, 260f), restoredColors.map { it.h })
        assertTrue(restoredColors.all { it.s > 74f && it.l == 65f })
    }

    private data class SeriesColor(
        val name: String,
        val color: HSLuv,
    )
}
