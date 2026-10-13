/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bytedeco.libfreenect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.bytedeco.libfreenect.global.freenect.FREENECT_LOG_ERROR;
import static org.bytedeco.libfreenect.global.freenect.freenect_init;
import static org.bytedeco.libfreenect.global.freenect.freenect_open_device_by_camera_serial;
import static org.bytedeco.libfreenect.global.freenect.freenect_set_log_callback;
import static org.bytedeco.libfreenect.global.freenect.freenect_set_log_level;
import static org.bytedeco.libfreenect.global.freenect.freenect_shutdown;

import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.libfreenect.freenect_context;
import org.bytedeco.libfreenect.freenect_device;
import org.bytedeco.libfreenect.freenect_log_cb;
import org.junit.jupiter.api.Test;

public class FreenectLogCallbackTest {
    @Test
    void routesNativeErrorsToTheConfiguredLogCallback() {
        String unknownSerial = "x".repeat(512);

        try (freenect_context context = new freenect_context();
                freenect_device device = new freenect_device();
                CapturingLogCallback callback = new CapturingLogCallback()) {
            assertThat(freenect_init(context, null)).isZero();
            try {
                freenect_set_log_level(context, FREENECT_LOG_ERROR);
                freenect_set_log_callback(context, callback);

                assertThat(freenect_open_device_by_camera_serial(context, device, unknownSerial))
                        .isNegative();
                assertThat(callback.level()).isEqualTo(FREENECT_LOG_ERROR);
                assertThat(callback.message())
                        .contains("freenect_open_device_by_camera_serial")
                        .contains(unknownSerial);
            } finally {
                freenect_set_log_callback(context, null);
                assertThat(freenect_shutdown(context)).isZero();
            }
        }
    }
}

final class CapturingLogCallback extends freenect_log_cb {
    private int level = -1;
    private String message;

    @Override
    public void call(freenect_context context, int receivedLevel, BytePointer receivedMessage) {
        level = receivedLevel;
        message = receivedMessage.getString();
    }

    int level() {
        return level;
    }

    String message() {
        return message;
    }
}
