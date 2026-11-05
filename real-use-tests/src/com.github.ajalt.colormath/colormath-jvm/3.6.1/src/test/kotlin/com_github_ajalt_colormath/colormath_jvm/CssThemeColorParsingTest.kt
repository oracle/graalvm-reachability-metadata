/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_ajalt_colormath.colormath_jvm

import com.github.ajalt.colormath.AngleUnit
import com.github.ajalt.colormath.Color
import com.github.ajalt.colormath.RenderCondition
import com.github.ajalt.colormath.calculate.firstWithContrast
import com.github.ajalt.colormath.calculate.wcagContrastRatio
import com.github.ajalt.colormath.formatCssString
import com.github.ajalt.colormath.parse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Exercises CSS color parsing and rendering: a web theme accepts modern CSS color syntax,
 * selects an accessible foreground, and emits normalized CSS for a browser stylesheet.
 */
public class CssThemeColorParsingTest {

    @Test
    public fun parsesThemeTokensAndRendersNormalizedCss(): Unit {
        val accent = Color.parse("oklch(62% 0.2 250 / 80%)")
        val background = Color.parse("#102030")
        val foreground = background.firstWithContrast(
            Color.parse("#ffffff"),
            Color.parse("#101010"),
            targetContrast = 4.5f,
        )

        assertEquals(0.8f, accent.alpha, 0.0001f)
        assertEquals(250f, accent.toOklch().h, 0.1f)
        assertEquals("#102030", background.toSRGB().toHex())
        assertEquals("#ffffff", foreground.toSRGB().toHex())
        assertTrue(background.wcagContrastRatio(foreground) >= 4.5f)
        assertEquals(
            "oklch(62% 0.2 250deg / 0.8)",
            accent.formatCssString(AngleUnit.DEGREES, RenderCondition.ALWAYS),
        )
    }

    @Test
    public fun rejectsUnsupportedTokensWithoutBreakingThemeLoading(): Unit {
        val rgb = Color.parse("rgb(255 128 0 / 50%)").toSRGB()

        assertThrows<IllegalArgumentException> { Color.parse("not-a-color") }
        assertEquals(255, rgb.redInt)
        assertEquals(128, rgb.greenInt)
        assertEquals(0, rgb.blueInt)
        assertEquals(0.5f, rgb.alpha, 0.0001f)
        assertEquals("rgb(255 128 0 / 0.5)", rgb.formatCssString())
    }
}
