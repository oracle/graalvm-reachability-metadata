/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_twelvemonkeys_common.common_image;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.IndexColorModel;
import java.awt.image.MemoryImageSource;

import com.twelvemonkeys.image.BufferedImageFactory;
import org.junit.jupiter.api.Test;

public class BufferedImageFactoryInnerConsumerTest {
    @Test
    void convertsPixelsProducedByImageSourceToBufferedImage() {
        int[] pixels = {
                0xff102030, 0xff405060,
                0xff708090, 0xffa0b0c0
        };
        MemoryImageSource source = new MemoryImageSource(2, 2, ColorModel.getRGBdefault(), pixels, 0, 2);

        BufferedImage image = new BufferedImageFactory(source).getBufferedImage();

        assertThat(image.getWidth()).isEqualTo(2);
        assertThat(image.getHeight()).isEqualTo(2);
        assertThat(image.getRGB(0, 0, 2, 2, null, 0, 2)).containsExactly(pixels);
    }

    @Test
    void preservesIndexedPixelsProducedAsBytes() {
        byte[] red = {0x00, 0x20, 0x40, 0x60};
        byte[] green = {0x10, 0x30, 0x50, 0x70};
        byte[] blue = {0x20, 0x40, 0x60, (byte) 0x80};
        IndexColorModel colorModel = new IndexColorModel(8, 4, red, green, blue);
        byte[] pixels = {0, 1, 2, 3};
        MemoryImageSource source = new MemoryImageSource(2, 2, colorModel, pixels, 0, 2);

        BufferedImage image = new BufferedImageFactory(source).getBufferedImage();

        assertThat(image.getColorModel()).isSameAs(colorModel);
        assertThat(image.getRGB(0, 0, 2, 2, null, 0, 2))
                .containsExactly(0xff001020, 0xff203040, 0xff405060, 0xff607080);
    }
}
