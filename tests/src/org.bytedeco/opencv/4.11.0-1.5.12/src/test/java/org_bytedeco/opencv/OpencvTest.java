/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bytedeco.opencv;

import static org.assertj.core.api.Assertions.assertThat;

import org.bytedeco.javacpp.ClassProperties;
import org.bytedeco.javacpp.Loader;
import org.bytedeco.opencv.presets.opencv_core;
import org.junit.jupiter.api.Test;

public class OpencvTest {
    @Test
    void loadsOpenCvPresetProperties() {
        ClassProperties properties =
                Loader.loadProperties(opencv_core.class, Loader.loadProperties(), true);

        assertThat(properties.isLoaded()).isTrue();
        assertThat(properties.getEffectiveClasses()).contains(opencv_core.class);
    }
}
