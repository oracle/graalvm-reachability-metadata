/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_ajalt_colormath.colormath_jvm

import com.github.ajalt.colormath.model.JzCzHz
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Exercises JzCzHz HDR color grading: a mastering tool adjusts perceptual lightness and exports a display preview.
 */
public class JzCzHzHdrGradingTest {

    @Test
    public fun gradesHdrColorAndExportsPreview(): Unit {
        val source = JzCzHz(0.08f, 0.04f, 210f, 0.9f)
        val graded = source.copy(j = 0.12f)
        val perceptualCoordinates = graded.toJzAzBz()
        val displayPreview = graded.toSRGB().clamp()
        val restored = perceptualCoordinates.toJzCzHz()

        assertEquals(0.12f, graded.j, 0.0001f)
        assertEquals(0.04f, graded.c, 0.0001f)
        assertEquals(210f, graded.h, 0.0001f)
        assertEquals(0.9f, displayPreview.alpha, 0.0001f)
        assertTrue(displayPreview.r in 0f..1f)
        assertTrue(displayPreview.g in 0f..1f)
        assertTrue(displayPreview.b in 0f..1f)
        assertEquals(graded.j, restored.j, 0.0001f)
        assertEquals(graded.c, restored.c, 0.0001f)
        assertEquals(graded.h, restored.h, 0.0001f)
    }
}
