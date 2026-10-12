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
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.OpenCVFrameGrabber;
import org.junit.jupiter.api.Test;

public class CameraDeviceTest {
    @Test
    void createsOpenCvGrabbersForEverySupportedSelector() throws Exception {
        CameraDevice.SettingsImplementation settings = configuredSettings(OpenCVFrameGrabber.class);
        CameraDevice camera = new CameraDevice(settings);
        CameraDevice.Settings activeSettings = (CameraDevice.Settings) camera.getSettings();

        activeSettings.setDeviceFile(new File("camera-input.avi"));
        assertConfiguredOpenCvGrabber(camera.createFrameGrabber());

        activeSettings.setDevicePath("camera-input.avi");
        assertConfiguredOpenCvGrabber(camera.createFrameGrabber());

        activeSettings.setDeviceNumber(0);
        assertConfiguredOpenCvGrabber(camera.createFrameGrabber());
    }

    @Test
    void fallsBackToTheBoxedDeviceNumberConstructor() throws Exception {
        CameraDevice.SettingsImplementation settings = configuredSettings(IntegerFrameGrabber.class);
        CameraDevice camera = new CameraDevice(settings);

        try (FrameGrabber grabber = camera.createFrameGrabber()) {
            assertThat(grabber).isInstanceOf(IntegerFrameGrabber.class);
            assertThat(((IntegerFrameGrabber) grabber).getDeviceNumber()).isZero();
            assertThat(grabber.getImageWidth()).isEqualTo(320);
            assertThat(grabber.getImageHeight()).isEqualTo(240);
        }
    }

    private static CameraDevice.SettingsImplementation configuredSettings(
            Class<? extends FrameGrabber> frameGrabber) {
        CameraDevice.SettingsImplementation settings = new CameraDevice.SettingsImplementation();
        settings.setFrameGrabber(frameGrabber);
        settings.setImageWidth(320);
        settings.setImageHeight(240);
        settings.setFrameRate(25.0);
        settings.setTimeout(10_000);
        return settings;
    }

    private static void assertConfiguredOpenCvGrabber(FrameGrabber grabber) throws Exception {
        try (grabber) {
            assertThat(grabber).isInstanceOf(OpenCVFrameGrabber.class);
            assertThat(grabber.getImageWidth()).isEqualTo(320);
            assertThat(grabber.getImageHeight()).isEqualTo(240);
            assertThat(grabber.getFrameRate()).isEqualTo(25.0);
            assertThat(grabber.getTimeout()).isEqualTo(10_000);
        }
    }
}
