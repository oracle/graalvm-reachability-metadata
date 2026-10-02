/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_twelvemonkeys_imageio.imageio_core;

import com.twelvemonkeys.imageio.color.ColorSpaces;
import java.awt.color.ColorSpace;
import java.awt.color.ICC_ColorSpace;
import java.awt.color.ICC_Profile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ColorProfilesTest {
    @Test
    void loadsBundledAdobeRgbProfileThroughColorSpaceApi() {
        ColorSpace colorSpace = ColorSpaces.getColorSpace(ColorSpaces.CS_ADOBE_RGB_1998);
        ICC_ColorSpace adobeRgb = assertInstanceOf(ICC_ColorSpace.class, colorSpace);

        assertEquals(ColorSpace.TYPE_RGB, adobeRgb.getType());
        assertEquals(3, adobeRgb.getNumComponents());
        assertTrue(adobeRgb.getProfile().getData().length > ICC_Profile.icHdrSize);

        float[] whitePoint = adobeRgb.toCIEXYZ(new float[] {1.0f, 1.0f, 1.0f});
        assertEquals(0.9642f, whitePoint[0], 0.01f);
        assertEquals(1.0f, whitePoint[1], 0.01f);
        assertEquals(0.8249f, whitePoint[2], 0.01f);
    }
}
