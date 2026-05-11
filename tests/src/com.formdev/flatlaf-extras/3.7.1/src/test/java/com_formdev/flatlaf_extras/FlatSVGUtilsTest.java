/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_formdev.flatlaf_extras;

import static org.assertj.core.api.Assertions.assertThat;

import com.formdev.flatlaf.extras.FlatSVGUtils;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

public class FlatSVGUtilsTest {
    private static final String ICON_RESOURCE = "/com_formdev/flatlaf_extras/icon.svg";

    @Test
    void rendersSvgFromClasspathResourceName() {
        BufferedImage image = FlatSVGUtils.svg2image(ICON_RESOURCE, 24, 20);

        assertThat(image.getWidth()).isEqualTo(24);
        assertThat(image.getHeight()).isEqualTo(20);
    }

    @Test
    void rendersScaledSvgFromClasspathResourceName() {
        BufferedImage image = FlatSVGUtils.svg2image(ICON_RESOURCE, 2f);

        assertThat(image.getWidth()).isEqualTo(24);
        assertThat(image.getHeight()).isEqualTo(20);
    }
}
