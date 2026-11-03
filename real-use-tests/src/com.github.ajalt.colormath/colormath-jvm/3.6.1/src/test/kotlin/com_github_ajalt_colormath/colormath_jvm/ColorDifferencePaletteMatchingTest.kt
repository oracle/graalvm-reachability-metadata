/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_ajalt_colormath.colormath_jvm

import com.github.ajalt.colormath.Color
import com.github.ajalt.colormath.calculate.differenceCIE2000
import com.github.ajalt.colormath.calculate.euclideanDistance
import com.github.ajalt.colormath.parse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Exercises perceptual color difference: a design tool matches an imported color to its brand palette.
 */
public class ColorDifferencePaletteMatchingTest {

    @Test
    public fun choosesClosestBrandSwatchByPerceptualDifference(): Unit {
        val importedColor = Color.parse("#3b82f6")
        val palette = listOf(
            BrandSwatch("ocean", Color.parse("#2563eb")),
            BrandSwatch("teal", Color.parse("#0f766e")),
            BrandSwatch("violet", Color.parse("#7c3aed")),
            BrandSwatch("ember", Color.parse("#ea580c")),
        )

        val closest = palette.minBy { importedColor.differenceCIE2000(it.color) }
        val exactMatchDistance = importedColor.differenceCIE2000(importedColor)
        val rgbDistance = importedColor.euclideanDistance(closest.color)

        assertEquals("ocean", closest.name)
        assertTrue(exactMatchDistance < 0.0001f)
        assertTrue(importedColor.differenceCIE2000(closest.color) < 15f)
        assertTrue(rgbDistance > 0f)
    }

    private data class BrandSwatch(
        val name: String,
        val color: Color,
    )
}
