/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_ajalt_colormath.colormath_jvm

import com.github.ajalt.colormath.Color
import com.github.ajalt.colormath.RenderCondition
import com.github.ajalt.colormath.calculate.differenceCIE2000
import com.github.ajalt.colormath.calculate.differenceCIE76
import com.github.ajalt.colormath.calculate.differenceCIE94
import com.github.ajalt.colormath.calculate.differenceCMC
import com.github.ajalt.colormath.calculate.differenceEz
import com.github.ajalt.colormath.calculate.euclideanDistance
import com.github.ajalt.colormath.calculate.isInSRGBGamut
import com.github.ajalt.colormath.calculate.mostContrasting
import com.github.ajalt.colormath.calculate.wcagContrastRatio
import com.github.ajalt.colormath.calculate.wcagLuminance
import com.github.ajalt.colormath.formatCssString
import com.github.ajalt.colormath.model.CMYK
import com.github.ajalt.colormath.model.HSL
import com.github.ajalt.colormath.model.LABColorSpaces
import com.github.ajalt.colormath.model.RGB
import com.github.ajalt.colormath.model.RGBColorSpaces
import com.github.ajalt.colormath.model.RGBInt
import com.github.ajalt.colormath.model.XYZColorSpaces
import com.github.ajalt.colormath.parse
import com.github.ajalt.colormath.parseOrNull
import com.github.ajalt.colormath.transform.HueAdjustments
import com.github.ajalt.colormath.transform.divideAlpha
import com.github.ajalt.colormath.transform.interpolate
import com.github.ajalt.colormath.transform.interpolator
import com.github.ajalt.colormath.transform.map
import com.github.ajalt.colormath.transform.mix
import com.github.ajalt.colormath.transform.multiplyAlpha
import com.github.ajalt.colormath.transform.sequence
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test

public class Colormath_jvmTest {
    @Test
    fun `creates srgb colors from hex integers arrays and clamps components`(): Unit {
        val hexColor = RGB("#336699cc")
        val integerColor = RGB.from255(51, 102, 153, 204)
        val arrayColor = RGB.create(floatArrayOf(0.2f, 0.4f, 0.6f, 0.8f))

        assertRgbClose(hexColor, 0.2f, 0.4f, 0.6f, 0.8f)
        assertRgbClose(integerColor, 0.2f, 0.4f, 0.6f, 0.8f)
        assertRgbClose(arrayColor, 0.2f, 0.4f, 0.6f, 0.8f)
        assertThat(hexColor.toHex(true, RenderCondition.ALWAYS)).isEqualTo("#336699cc")
        assertThat(hexColor.toArray().toList()).hasSize(4)

        val unclamped = RGB(1.4, -0.25, 0.5, 1.2)
        val clamped = unclamped.clamp()
        assertRgbClose(clamped, 1f, 0f, 0.5f, 1f)
    }

    @Test
    fun `packs srgb channels as RGBInt values`(): Unit {
        val packed = RGBInt(51, 102, 153, 204)
        val fromFloats = RGBInt(0.2f, 0.4f, 0.6f, 0.8f)
        val fromRgba = RGBInt.fromRGBA(0x336699ccu)

        assertThat(fromFloats).isEqualTo(packed)
        assertThat(fromRgba).isEqualTo(packed)
        assertThat(packed.toRGBA()).isEqualTo(0x336699ccu)
        assertThat(packed.toHex(true, RenderCondition.ALWAYS)).isEqualTo("#336699cc")
        assertRgbClose(packed.toSRGB(), 0.2f, 0.4f, 0.6f, 0.8f)
        assertClose(packed.redFloat, 0.2f)
        assertClose(packed.greenFloat, 0.4f)
        assertClose(packed.blueFloat, 0.6f)
    }

    @Test
    fun `converts between common cylindrical perceptual and subtractive color models`(): Unit {
        val color = RGB("#336699")

        val hsl = color.toHSL()
        assertClose(hsl.h, 210f)
        assertClose(hsl.s, 0.5f)
        assertClose(hsl.l, 0.4f)
        assertRgbClose(hsl.toSRGB(), 0.2f, 0.4f, 0.6f)

        val hsv = color.toHSV()
        assertClose(hsv.h, 210f)
        assertClose(hsv.s, 2f / 3f)
        assertClose(hsv.v, 0.6f)
        assertRgbClose(hsv.toSRGB(), 0.2f, 0.4f, 0.6f)

        val hwb = color.toHWB()
        assertClose(hwb.h, 210f)
        assertClose(hwb.w, 0.2f)
        assertClose(hwb.b, 0.4f)
        assertRgbClose(hwb.toSRGB(), 0.2f, 0.4f, 0.6f)

        val cmyk = color.toCMYK()
        assertClose(cmyk.c, 2f / 3f)
        assertClose(cmyk.m, 1f / 3f)
        assertClose(cmyk.y, 0f)
        assertClose(cmyk.k, 0.4f)
        assertRgbClose(cmyk.toSRGB(), 0.2f, 0.4f, 0.6f)

        val fromPercentCmyk = CMYK(67, 33, 0, 40)
        assertRgbClose(fromPercentCmyk.toSRGB(), 0.198f, 0.402f, 0.6f, tolerance = 0.003f)
    }

    @Test
    fun `round trips through xyz lab luv ok and wide gamut rgb spaces`(): Unit {
        val displayP3 = RGBColorSpaces.DisplayP3(0.95, 0.25, 0.1, 0.7)
        val srgb = displayP3.convertTo(RGB)
        val displayP3RoundTrip = srgb.convertTo(RGBColorSpaces.DisplayP3)
        assertRgbClose(displayP3RoundTrip, 0.95f, 0.25f, 0.1f, 0.7f, tolerance = 0.0005f)

        val xyz65 = displayP3.toXYZ()
        val xyz50 = xyz65.adaptTo(XYZColorSpaces.XYZ50)
        assertThat(xyz50.space).isEqualTo(XYZColorSpaces.XYZ50)
        assertThat(xyz50.adaptTo(XYZColorSpaces.XYZ65).space).isEqualTo(XYZColorSpaces.XYZ65)

        val lab50 = xyz50.toLAB()
        assertThat(lab50.space).isEqualTo(LABColorSpaces.LAB50)
        assertRgbClose(lab50.toSRGB(), srgb.r, srgb.g, srgb.b, 0.7f, tolerance = 0.001f)

        val lch = srgb.toLCHab()
        assertRgbClose(lch.toLAB().toSRGB(), srgb.r, srgb.g, srgb.b, 0.7f, tolerance = 0.001f)
        assertRgbClose(
            srgb.toLUV().toLCHuv().toLUV().toSRGB(),
            srgb.r,
            srgb.g,
            srgb.b,
            0.7f,
            tolerance = 0.001f,
        )
        assertRgbClose(
            srgb.toOklab().toOklch().toOklab().toSRGB(),
            srgb.r,
            srgb.g,
            srgb.b,
            0.7f,
            tolerance = 0.001f,
        )
    }

    @Test
    fun `parses renders and rejects css color strings`(): Unit {
        val named = Color.parse("rebeccapurple").toSRGB()
        assertThat(named.toHex()).isEqualTo("#663399")

        val rgbCss = Color.parse("rgb(51 102 153 / 80%)").toSRGB()
        assertRgbClose(rgbCss, 0.2f, 0.4f, 0.6f, 0.8f)
        assertThat(rgbCss.toHex(true, RenderCondition.ALWAYS)).isEqualTo("#336699cc")

        val hslCss = Color.parse("hsl(210 50% 40% / 0.8)").toSRGB()
        assertRgbClose(hslCss, 0.2f, 0.4f, 0.6f, 0.8f)

        val rendered = rgbCss.formatCssString()
        val renderedRoundTrip = Color.parse(rendered).toSRGB()
        assertRgbClose(renderedRoundTrip, rgbCss.r, rgbCss.g, rgbCss.b, rgbCss.alpha, tolerance = 0.001f)

        assertThat(Color.parseOrNull("definitely-not-a-css-color")).isNull()
        assertThatIllegalArgumentException().isThrownBy {
            Color.parse("definitely-not-a-css-color")
        }
    }

    @Test
    fun `calculates color differences contrast and gamut membership`(): Unit {
        val black = RGB("#000000")
        val white = RGB("#ffffff")
        val red = RGB("#ff0000")
        val blue = RGB("#0000ff")

        assertClose(black.wcagLuminance(), 0f)
        assertClose(white.wcagLuminance(), 1f)
        assertClose(black.wcagContrastRatio(white), 21f)
        assertThat(RGB("#777777").mostContrasting(black, white)).isEqualTo(black)

        assertClose(red.differenceCIE76(red), 0f)
        assertClose(red.differenceCIE2000(red), 0f)
        assertThat(red.differenceCIE76(blue)).isGreaterThan(0f)
        assertThat(red.differenceCIE94(blue)).isGreaterThan(0f)
        assertThat(red.differenceCIE2000(blue)).isGreaterThan(0f)
        assertThat(red.differenceCMC(blue)).isGreaterThan(0f)
        assertThat(red.differenceEz(blue)).isGreaterThan(0f)
        assertThat(red.toOklab().euclideanDistance(blue.toOklab())).isGreaterThan(0f)

        assertThat(red.isInSRGBGamut()).isTrue()
        assertThat(RGBColorSpaces.DisplayP3(1.0, 0.0, 0.0, 1.0).isInSRGBGamut()).isFalse()
    }

    @Test
    fun `maps premultiplies and divides alpha without losing the color space`(): Unit {
        val color = RGB(0.2, 0.4, 0.6, 0.5)

        val inverted = color.map { components: FloatArray ->
            floatArrayOf(1f - components[0], 1f - components[1], 1f - components[2], components[3])
        }
        assertRgbClose(inverted, 0.8f, 0.6f, 0.4f, 0.5f)
        assertThat(inverted.space).isEqualTo(RGB)

        val premultiplied = color.multiplyAlpha()
        assertRgbClose(premultiplied, 0.1f, 0.2f, 0.3f, 0.5f)
        assertRgbClose(premultiplied.divideAlpha(), 0.2f, 0.4f, 0.6f, 0.5f)
    }

    @Test
    fun `interpolates individual colors and samples multi stop gradients`(): Unit {
        val red = RGB("#ff0000")
        val green = RGB("#00ff00")
        val blue = RGB("#0000ff")

        val purple = red.interpolate(blue, 0.5)
        assertRgbClose(purple, 0.5f, 0f, 0.5f)

        val gradient = RGB.interpolator {
            stop(red, 0.0) {}
            stop(green, 0.5) {}
            stop(blue, 1.0) {}
        }
        assertRgbClose(gradient.interpolate(0.0), 1f, 0f, 0f)
        assertRgbClose(gradient.interpolate(0.5), 0f, 1f, 0f)
        assertRgbClose(gradient.interpolate(1.0), 0f, 0f, 1f)

        val samples = gradient.sequence(5).toList()
        assertThat(samples).hasSize(5)
        assertRgbClose(samples.first(), 1f, 0f, 0f)
    }

    @Test
    fun `mixes weighted colors and controls hue interpolation direction`(): Unit {
        val red = RGB("#ff0000")
        val blue = RGB("#0000ff")

        val semiTransparentPurple = RGB.mix(red, 0.2, blue, 0.3).toSRGB()
        assertRgbClose(semiTransparentPurple, 0.4f, 0f, 0.6f, 0.5f)

        val nearRed = HSL(10, 1, 0.5)
        val nearMagenta = HSL(350, 1, 0.5)
        assertClose(HSL.mix(nearRed, nearMagenta).toHSL().h, 0f)
        assertClose(HSL.mix(nearRed, nearMagenta, HueAdjustments.longer).toHSL().h, 180f)
    }

    @Test
    fun `converts to terminal palette colors and preserves alpha in perceptual spaces`(): Unit {
        val color = HSL(210, 0.5, 0.4, 0.65)
        val srgb = color.toSRGB()

        val ansi16 = srgb.toAnsi16()
        val ansi256 = srgb.toAnsi256()
        assertThat(ansi16.code).isBetween(0, 107)
        assertThat(ansi256.code).isBetween(0, 255)

        val ansi256Srgb = ansi256.toSRGB()
        assertRgbClose(
            ansi256Srgb.clamp(),
            ansi256Srgb.r.coerceIn(0f, 1f),
            ansi256Srgb.g.coerceIn(0f, 1f),
            ansi256Srgb.b.coerceIn(0f, 1f),
        )

        assertClose(srgb.toJzAzBz().toSRGB().alpha, 0.65f)
        assertClose(srgb.toICtCp().toSRGB().alpha, 0.65f)
        assertClose(srgb.toHSLuv().toSRGB().alpha, 0.65f)
        assertClose(srgb.toHPLuv().toSRGB().alpha, 0.65f)
    }

    private fun assertRgbClose(
        actual: RGB,
        r: Float,
        g: Float,
        b: Float,
        alpha: Float = 1f,
        tolerance: Float = 0.0001f,
    ): Unit {
        assertClose(actual.r, r, tolerance)
        assertClose(actual.g, g, tolerance)
        assertClose(actual.b, b, tolerance)
        assertClose(actual.alpha, alpha, tolerance)
    }

    private fun assertClose(actual: Float, expected: Float, tolerance: Float = 0.0001f): Unit {
        assertThat(actual).isCloseTo(expected, within(tolerance))
    }
}
