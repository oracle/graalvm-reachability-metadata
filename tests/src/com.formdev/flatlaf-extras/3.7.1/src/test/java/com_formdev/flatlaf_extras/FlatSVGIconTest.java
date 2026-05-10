/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_formdev.flatlaf_extras;

import static org.assertj.core.api.Assertions.assertThat;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import org.junit.jupiter.api.Test;

public class FlatSVGIconTest {
    private static final String ICON_RESOURCE = "com_formdev/flatlaf_extras/icon.svg";

    @Test
    void loadsSvgIconFromClasspathResourceName() {
        ClassLoader classLoader = FlatSVGIconTest.class.getClassLoader();
        FlatSVGIcon icon = new FlatSVGIcon(ICON_RESOURCE, classLoader);

        assertThat(icon.hasFound()).isTrue();
        assertThat(icon.getName()).isEqualTo(ICON_RESOURCE);
        assertThat(icon.getClassLoader()).isSameAs(classLoader);
        assertThat(icon.getIconWidth()).isPositive();
        assertThat(icon.getIconHeight()).isPositive();
    }
}
