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
}
