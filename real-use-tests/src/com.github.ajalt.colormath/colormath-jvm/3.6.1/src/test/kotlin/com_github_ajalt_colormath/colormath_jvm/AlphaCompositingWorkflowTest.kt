/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_ajalt_colormath.colormath_jvm

import com.github.ajalt.colormath.model.RGB
import com.github.ajalt.colormath.transform.divideAlpha
import com.github.ajalt.colormath.transform.mix
import com.github.ajalt.colormath.transform.multiplyAlpha
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Exercises alpha compositing: an image editor prepares translucent overlays and blends color layers for export.
 */
public class AlphaCompositingWorkflowTest {

    @Test
    public fun preparesTransparentLayerAndBlendsExportColors(): Unit {
        val overlay = RGB(0.2f, 0.4f, 0.6f, 0.5f)
        val premultiplied = overlay.multiplyAlpha()
        val restored = premultiplied.divideAlpha()
        val exportColor = RGB.mix(
            RGB("#ff0000"),
            0.2,
            RGB("#0000ff"),
            0.3,
        ).toSRGB()

        assertEquals(0.1f, premultiplied.r, 0.0001f)
        assertEquals(0.2f, premultiplied.g, 0.0001f)
        assertEquals(0.3f, premultiplied.b, 0.0001f)
        assertEquals(0.5f, premultiplied.alpha, 0.0001f)
        assertEquals(overlay.r, restored.r, 0.0001f)
        assertEquals(overlay.g, restored.g, 0.0001f)
        assertEquals(overlay.b, restored.b, 0.0001f)
        assertEquals("#66009980", exportColor.toHex())
        assertEquals(0.5f, exportColor.alpha, 0.0001f)
    }
}
