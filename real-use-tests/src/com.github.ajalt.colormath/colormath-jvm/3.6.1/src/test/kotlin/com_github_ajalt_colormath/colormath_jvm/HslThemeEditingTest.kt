/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_ajalt_colormath.colormath_jvm

import com.github.ajalt.colormath.model.HSL
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Exercises HSL color modeling: a theme editor adjusts a swatch and previews it in RGB and HSV.
 */
public class HslThemeEditingTest {

    @Test
    public fun adjustsThemeSwatchAcrossColorModels(): Unit {
        val selected = HSL(210f, 0.75f, 0.45f, 0.8f)
        val brighter = selected.copy(l = 0.60f)
        val preview = brighter.toSRGB()
        val hsv = brighter.toHSV()
        val restored = preview.toHSL()

        assertEquals(210f, brighter.h, 0.0001f)
        assertEquals(0.60f, brighter.l, 0.0001f)
        assertEquals(0.8f, preview.alpha, 0.0001f)
        assertEquals(210f, hsv.h, 0.1f)
        assertTrue(preview.redInt > selected.toSRGB().redInt)
        assertEquals(brighter.h, restored.h, 0.1f)
        assertEquals(brighter.s, restored.s, 0.01f)
        assertEquals(brighter.l, restored.l, 0.01f)
    }
}
