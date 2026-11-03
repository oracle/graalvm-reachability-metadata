/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_ajalt_colormath.colormath_jvm

import com.github.ajalt.colormath.model.RGBColorSpaces
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Exercises color-profile conversion: a display pipeline converts Display P3 colors before exporting an sRGB asset.
 */
public class DisplayProfileConversionTest {

    @Test
    public fun convertsDisplayP3AssetToSrgbForExport(): Unit {
        val displayColor = RGBColorSpaces.DisplayP3(0.9f, 0.2f, 0.1f)
        val exportedColor = displayColor.convertTo(RGBColorSpaces.SRGB)
        val restoredColor = exportedColor.convertTo(RGBColorSpaces.DisplayP3)

        assertEquals("#fa0500", exportedColor.toHex())
        assertTrue(exportedColor.redInt >= displayColor.redInt)
        assertEquals(displayColor.r, restoredColor.r, 0.0001f)
        assertEquals(displayColor.g, restoredColor.g, 0.0001f)
        assertEquals(displayColor.b, restoredColor.b, 0.0001f)
    }
}
