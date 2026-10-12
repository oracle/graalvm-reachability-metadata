/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bytedeco.javacv;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.awt.GraphicsEnvironment;
import java.awt.HeadlessException;
import org.bytedeco.javacv.CanvasFrame;
import org.bytedeco.javacv.ProjectorDevice;
import org.junit.jupiter.api.Test;

public class ProjectorDeviceTest {
    @Test
    void rejectsUnavailableScreenWhenCreatingStandardCanvasFrame() {
        if (GraphicsEnvironment.isHeadless()) {
            assertThatExceptionOfType(HeadlessException.class)
                    .isThrownBy(() -> new ProjectorDevice("Calibration projector"));
        } else {
            ProjectorDevice projector = new ProjectorDevice("Calibration projector");
            ProjectorDevice.Settings settings = (ProjectorDevice.Settings) projector.getSettings();
            settings.setUseOpenGL(false);
            settings.setScreenNumber(Integer.MAX_VALUE);

            assertThatExceptionOfType(CanvasFrame.Exception.class)
                    .isThrownBy(projector::createCanvasFrame)
                    .withMessageContaining("Screen number " + Integer.MAX_VALUE + " not found");
        }
    }
}
