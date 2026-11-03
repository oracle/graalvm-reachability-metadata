/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_ajalt_colormath.colormath_jvm

import com.github.ajalt.colormath.calculate.isInSRGBGamut
import com.github.ajalt.colormath.model.HWB
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Exercises HWB color modeling: a browser theme editor lightens a hue and exports a translucent preview swatch.
 */
public class HwbBrowserThemeTest {

    @Test
    public fun lightensThemeSwatchForBrowserPreview(): Unit {
        val original = HWB(210f, 0.2f, 0.1f, 0.8f)
        val edited = original.copy(w = 0.3f)
        val preview = edited.toSRGB()
        val restored = preview.toHWB()
        val hsv = edited.toHSV()

        assertEquals(210f, edited.h, 0.0001f)
        assertEquals(0.3f, edited.w, 0.0001f)
        assertEquals(0.1f, edited.b, 0.0001f)
        assertEquals("#4d99e6cc", preview.toHex())
        assertTrue(preview.isInSRGBGamut())
        assertEquals(0.8f, preview.alpha, 0.0001f)
        assertEquals(210f, hsv.h, 0.1f)
        assertEquals(edited.h, restored.h, 0.1f)
        assertEquals(edited.w, restored.w, 0.0001f)
        assertEquals(edited.b, restored.b, 0.0001f)
        assertTrue(preview.redInt > original.toSRGB().redInt)
    }
}
