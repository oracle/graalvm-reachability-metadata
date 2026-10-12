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
    void createsOpenCvGrabbersForEachDeviceSelector() throws Exception {
        CameraDevice camera = cameraUsing(OpenCVFrameGrabber.class);
        CameraDevice.Settings settings = (CameraDevice.Settings) camera.getSettings();

        settings.setDeviceFile(new File("camera-input.avi"));
        assertConfiguredOpenCvGrabber(camera.createFrameGrabber());

        settings.setDevicePath("camera-input.avi");
        assertConfiguredOpenCvGrabber(camera.createFrameGrabber());

        settings.setDeviceNumber(3);
        assertConfiguredOpenCvGrabber(camera.createFrameGrabber());
    }

    @Test
    void createsGrabberWithBoxedDeviceNumberConstructor() throws Exception {
        CameraDevice camera = cameraUsing(IntegerFrameGrabber.class);

        try (FrameGrabber grabber = camera.createFrameGrabber()) {
            assertThat(grabber).isInstanceOf(IntegerFrameGrabber.class);
            assertThat(((IntegerFrameGrabber) grabber).getDeviceNumber()).isZero();
            assertConfiguration(grabber);
        }
    }

    private static CameraDevice cameraUsing(Class<? extends FrameGrabber> frameGrabber) throws Exception {
        CameraDevice.SettingsImplementation settings = new CameraDevice.SettingsImplementation();
        settings.setFrameGrabber(frameGrabber);
        settings.setFormat("MJPG");
        settings.setImageWidth(320);
        settings.setImageHeight(240);
        settings.setFrameRate(25.0);
        settings.setTriggerMode(true);
        settings.setBitsPerPixel(24);
        settings.setImageMode(FrameGrabber.ImageMode.GRAY);
        settings.setTimeout(10_000);
        settings.setNumBuffers(6);
        settings.setResponseGamma(1.8);
        settings.setDeinterlace(true);
        return new CameraDevice(settings);
    }

    private static void assertConfiguredOpenCvGrabber(FrameGrabber grabber) throws Exception {
        try (grabber) {
            assertThat(grabber).isInstanceOf(OpenCVFrameGrabber.class);
            assertConfiguration(grabber);
        }
    }

    private static void assertConfiguration(FrameGrabber grabber) {
        assertThat(grabber.getFormat()).isEqualTo("MJPG");
        assertThat(grabber.getImageWidth()).isEqualTo(320);
        assertThat(grabber.getImageHeight()).isEqualTo(240);
        assertThat(grabber.getFrameRate()).isEqualTo(25.0);
        assertThat(grabber.isTriggerMode()).isTrue();
        assertThat(grabber.getBitsPerPixel()).isEqualTo(24);
        assertThat(grabber.getImageMode()).isEqualTo(FrameGrabber.ImageMode.GRAY);
        assertThat(grabber.getTimeout()).isEqualTo(10_000);
        assertThat(grabber.getNumBuffers()).isEqualTo(6);
        assertThat(grabber.getGamma()).isEqualTo(1.8);
        assertThat(grabber.isDeinterlace()).isTrue();
    }
}
