/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bytedeco.javacv;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import org.bytedeco.javacv.CameraDevice;
import org.junit.jupiter.api.Test;

public class CameraDeviceInnerSettingsImplementationTest {
    @Test
    void validatesSelectorsAndDeviceDescriptions() throws Exception {
        CameraDevice.SettingsImplementation settings = new CameraDevice.SettingsImplementation();

        File deviceFile = new File("camera-input.avi");
        settings.setDeviceFile(deviceFile);
        assertThat(settings.getDeviceFile()).isEqualTo(deviceFile);
        assertThat(settings.getDeviceNumber()).isNull();

        settings.setDevicePath("camera-input.avi");
        assertThat(settings.getDevicePath()).isEqualTo("camera-input.avi");
        assertThat(settings.getDeviceFile()).isNull();

        settings.setDeviceNumber(0);
        assertThat(settings.getDeviceNumber()).isZero();
        assertThat(settings.getDevicePath()).isNull();

        settings.setFrameGrabber(IntegerFrameGrabber.class);
        assertThat(settings.getDescription()).isEqualTo("Integer camera");
    }
}
