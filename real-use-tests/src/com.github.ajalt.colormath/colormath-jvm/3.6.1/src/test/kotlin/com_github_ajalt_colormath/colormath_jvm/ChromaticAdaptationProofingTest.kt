/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_ajalt_colormath.colormath_jvm

import com.github.ajalt.colormath.model.LABColorSpaces
import com.github.ajalt.colormath.model.XYZColorSpaces
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Exercises chromatic adaptation: a print proofing application moves a D50 measurement into the D65 display space before export.
 */
public class ChromaticAdaptationProofingTest {

    @Test
    public fun adaptsPrintMeasurementForDisplayExport(): Unit {
        val measuredSwatch = LABColorSpaces.LAB50(62f, 38f, 24f)
        val printMeasurement = measuredSwatch.toXYZ()
        val displayMeasurement = printMeasurement.adaptTo(XYZColorSpaces.XYZ65)
        val displayPreview = displayMeasurement.toSRGB()
        val restoredMeasurement = displayMeasurement.adaptTo(XYZColorSpaces.XYZ50).toLAB()

        assertEquals(XYZColorSpaces.XYZ50, printMeasurement.space)
        assertEquals(XYZColorSpaces.XYZ65, displayMeasurement.space)
        assertNotEquals(printMeasurement.x, displayMeasurement.x)
        assertTrue(displayPreview.toHex().startsWith("#"))
        assertEquals(measuredSwatch.l, restoredMeasurement.l, 0.0001f)
        assertEquals(measuredSwatch.a, restoredMeasurement.a, 0.0001f)
        assertEquals(measuredSwatch.b, restoredMeasurement.b, 0.0001f)
    }
}
