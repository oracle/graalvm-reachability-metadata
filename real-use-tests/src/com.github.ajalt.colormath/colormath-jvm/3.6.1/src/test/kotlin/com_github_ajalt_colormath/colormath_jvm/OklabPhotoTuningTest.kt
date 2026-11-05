/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_ajalt_colormath.colormath_jvm

import com.github.ajalt.colormath.model.Oklab
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Exercises Oklab photo tuning: an image editor raises perceptual lightness before exporting a web preview.
 */
public class OklabPhotoTuningTest {

    @Test
    public fun raisesPerceptualLightnessForWebPreview(): Unit {
        val source = Oklab(0.58f, 0.08f, -0.06f, 0.9f)
        val tuned = source.copy(l = 0.68f)
        val preview = tuned.toSRGB()
        val recovered = preview.toOklab()

        assertEquals(0.68f, tuned.l, 0.0001f)
        assertEquals(0.9f, preview.alpha, 0.0001f)
        assertTrue(preview.r in 0f..1f)
        assertTrue(preview.g in 0f..1f)
        assertTrue(preview.b in 0f..1f)
        assertEquals(tuned.l, recovered.l, 0.0001f)
        assertEquals(tuned.a, recovered.a, 0.0001f)
        assertEquals(tuned.b, recovered.b, 0.0001f)
        assertTrue(preview.redInt > source.toSRGB().redInt)
    }
}
