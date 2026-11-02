/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_ajalt_colormath.colormath_jvm

import com.github.ajalt.colormath.Color
import com.github.ajalt.colormath.model.CMYK
import com.github.ajalt.colormath.parse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Exercises CMYK print separation: a preflight tool converts a brand swatch into process inks and checks its proof color.
 */
public class CmykPrintPreflightTest {

    @Test
    public fun preparesBrandSwatchForProcessPrinting(): Unit {
        val brandColor = Color.parse("#336699")
        val separation = brandColor.toCMYK()
        val proof = separation.toSRGB()
        val explicitInkMix = CMYK(0.6667f, 0.3333f, 0f, 0.4f)

        assertEquals(0.6667f, separation.c, 0.001f)
        assertEquals(0.3333f, separation.m, 0.001f)
        assertEquals(0f, separation.y, 0.001f)
        assertEquals(0.4f, separation.k, 0.001f)
        assertEquals("#336699", proof.toHex())
        assertTrue(explicitInkMix.toSRGB().blueInt > explicitInkMix.toSRGB().redInt)
    }
}
