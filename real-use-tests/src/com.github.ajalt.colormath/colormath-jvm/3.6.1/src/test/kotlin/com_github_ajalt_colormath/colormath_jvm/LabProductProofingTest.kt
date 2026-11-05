/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_ajalt_colormath.colormath_jvm

import com.github.ajalt.colormath.model.LAB
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Exercises CIELAB color proofing: a print workflow converts a measured Lab swatch into an sRGB preview.
 */
public class LabProductProofingTest {

    @Test
    public fun convertsMeasuredLabSwatchForDigitalProofing(): Unit {
        val measuredSwatch = LAB(62f, 38f, 24f, 0.75f)
        val proofPreview = measuredSwatch.toSRGB()
        val cylindricalCoordinates = measuredSwatch.toLCHab()
        val restoredMeasurement = cylindricalCoordinates.toLAB()

        assertEquals(62f, measuredSwatch.l, 0.0001f)
        assertTrue(cylindricalCoordinates.c > 0f)
        assertTrue(cylindricalCoordinates.h in 0f..360f)
        assertEquals(measuredSwatch.l, restoredMeasurement.l, 0.0001f)
        assertEquals(measuredSwatch.a, restoredMeasurement.a, 0.0001f)
        assertEquals(measuredSwatch.b, restoredMeasurement.b, 0.0001f)
        assertEquals(measuredSwatch.alpha, proofPreview.alpha, 0.0001f)
        assertTrue(proofPreview.toHex().startsWith("#"))
    }
}
