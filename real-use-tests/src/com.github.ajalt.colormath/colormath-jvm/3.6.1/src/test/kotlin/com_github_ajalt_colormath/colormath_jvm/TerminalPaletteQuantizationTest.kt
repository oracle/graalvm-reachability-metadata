/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_ajalt_colormath.colormath_jvm

import com.github.ajalt.colormath.Color
import com.github.ajalt.colormath.model.Ansi16
import com.github.ajalt.colormath.model.Ansi256
import com.github.ajalt.colormath.parse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Exercises terminal palette conversion: a command-line dashboard maps its theme colors to ANSI output.
 */
public class TerminalPaletteQuantizationTest {

    @Test
    public fun exportsThemeColorsForAnsiTerminal(): Unit {
        val theme = listOf(
            ThemeColor("error", Color.parse("#ff0000")),
            ThemeColor("success", Color.parse("#00ff00")),
            ThemeColor("link", Color.parse("#0000ff")),
        )

        val ansiCodes = theme.associate { it.name to it.color.toAnsi256().code }
        val terminalColors = theme.associate { it.name to it.color.toAnsi256().toSRGB().toHex() }

        assertEquals(mapOf("error" to 196, "success" to 46, "link" to 21), ansiCodes)
        assertEquals(
            mapOf("error" to "#ff0000", "success" to "#00ff00", "link" to "#0000ff"),
            terminalColors,
        )
    }

    @Test
    public fun reducesThemeColorsToClassicSixteenColorPalette(): Unit {
        val warning = Color.parse("#ffff00")
        val terminalColor: Ansi16 = warning.toAnsi16()
        val restored = terminalColor.toSRGB()

        assertEquals(93, terminalColor.code)
        assertEquals("#ffff00", restored.toHex())
    }

    private data class ThemeColor(
        val name: String,
        val color: Color,
    )
}
