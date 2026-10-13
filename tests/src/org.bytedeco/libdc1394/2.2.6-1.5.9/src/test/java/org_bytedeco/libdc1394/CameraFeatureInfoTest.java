/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bytedeco.libdc1394;

import static org.assertj.core.api.Assertions.assertThat;
import static org.bytedeco.libdc1394.global.dc1394.DC1394_FEATURE_MODE_MANUAL;
import static org.bytedeco.libdc1394.global.dc1394.DC1394_FEATURE_SHUTTER;
import static org.bytedeco.libdc1394.global.dc1394.DC1394_TRUE;

import org.bytedeco.libdc1394.dc1394feature_info_t;
import org.junit.jupiter.api.Test;

public class CameraFeatureInfoTest {
    @Test
    void representsManualAbsoluteShutterConfiguration() {
        try (dc1394feature_info_t shutter = new dc1394feature_info_t()) {
            shutter.id(DC1394_FEATURE_SHUTTER)
                    .available(DC1394_TRUE)
                    .absolute_capable(DC1394_TRUE)
                    .on_off_capable(DC1394_TRUE)
                    .is_on(DC1394_TRUE)
                    .current_mode(DC1394_FEATURE_MODE_MANUAL)
                    .min(1)
                    .max(4095)
                    .value(512)
                    .abs_control(DC1394_TRUE)
                    .abs_min(0.001f)
                    .abs_max(1.0f)
                    .abs_value(0.025f);

            assertThat(shutter.id()).isEqualTo(DC1394_FEATURE_SHUTTER);
            assertThat(shutter.available()).isEqualTo(DC1394_TRUE);
            assertThat(shutter.absolute_capable()).isEqualTo(DC1394_TRUE);
            assertThat(shutter.on_off_capable()).isEqualTo(DC1394_TRUE);
            assertThat(shutter.is_on()).isEqualTo(DC1394_TRUE);
            assertThat(shutter.current_mode()).isEqualTo(DC1394_FEATURE_MODE_MANUAL);
            assertThat(shutter.min()).isEqualTo(1);
            assertThat(shutter.max()).isEqualTo(4095);
            assertThat(shutter.value()).isEqualTo(512);
            assertThat(shutter.abs_control()).isEqualTo(DC1394_TRUE);
            assertThat(shutter.abs_min()).isEqualTo(0.001f);
            assertThat(shutter.abs_max()).isEqualTo(1.0f);
            assertThat(shutter.abs_value()).isEqualTo(0.025f);
        }
    }
}
