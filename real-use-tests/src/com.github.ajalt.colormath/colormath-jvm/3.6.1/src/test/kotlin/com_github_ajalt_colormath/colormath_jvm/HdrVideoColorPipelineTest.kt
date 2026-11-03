/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_ajalt_colormath.colormath_jvm

import com.github.ajalt.colormath.model.ICtCp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Exercises HDR video color encoding: a grading tool converts an ICtCp sample to BT.2020 for delivery and to sRGB for a preview.
 */
public class HdrVideoColorPipelineTest {

    @Test
    public fun convertsHdrSampleForDeliveryAndPreview(): Unit {
        val hdrSample = ICtCp(0.62f, 0.08f, -0.04f)
        val deliveryColor = hdrSample.toBT2020()
        val previewColor = hdrSample.toSRGB()
        val displayPreview = previewColor.clamp()
        val recoveredSample = deliveryColor.toICtCp()

        assertEquals(0.62f, recoveredSample.i, 0.0001f)
        assertEquals(0.08f, recoveredSample.ct, 0.0001f)
        assertEquals(-0.04f, recoveredSample.cp, 0.0001f)
        assertEquals(1f, deliveryColor.alpha, 0.0001f)
        assertEquals(1f, previewColor.alpha, 0.0001f)
        assertTrue(deliveryColor.r > 1f)
        assertTrue(deliveryColor.g > 1f)
        assertTrue(deliveryColor.b > 1f)
        assertEquals(1f, displayPreview.r, 0.0001f)
        assertEquals(1f, displayPreview.g, 0.0001f)
        assertEquals(1f, displayPreview.b, 0.0001f)
    }
}
