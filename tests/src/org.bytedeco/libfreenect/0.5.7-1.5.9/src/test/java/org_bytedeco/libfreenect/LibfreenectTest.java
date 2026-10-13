/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bytedeco.libfreenect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.bytedeco.libfreenect.global.freenect.FREENECT_DEPTH_DUMMY;
import static org.bytedeco.libfreenect.global.freenect.FREENECT_DEVICE_CAMERA;
import static org.bytedeco.libfreenect.global.freenect.FREENECT_LOG_WARNING;
import static org.bytedeco.libfreenect.global.freenect.FREENECT_RESOLUTION_DUMMY;
import static org.bytedeco.libfreenect.global.freenect.FREENECT_VIDEO_DUMMY;
import static org.bytedeco.libfreenect.global.freenect.TILT_STATUS_MOVING;
import static org.bytedeco.libfreenect.global.freenect.freenect_enabled_subdevices;
import static org.bytedeco.libfreenect.global.freenect.freenect_find_depth_mode;
import static org.bytedeco.libfreenect.global.freenect.freenect_find_video_mode;
import static org.bytedeco.libfreenect.global.freenect.freenect_get_depth_mode;
import static org.bytedeco.libfreenect.global.freenect.freenect_get_depth_mode_count;
import static org.bytedeco.libfreenect.global.freenect.freenect_get_mks_accel;
import static org.bytedeco.libfreenect.global.freenect.freenect_get_tilt_degs;
import static org.bytedeco.libfreenect.global.freenect.freenect_get_tilt_status;
import static org.bytedeco.libfreenect.global.freenect.freenect_get_video_mode;
import static org.bytedeco.libfreenect.global.freenect.freenect_get_video_mode_count;
import static org.bytedeco.libfreenect.global.freenect.freenect_init;
import static org.bytedeco.libfreenect.global.freenect.freenect_num_devices;
import static org.bytedeco.libfreenect.global.freenect.freenect_select_subdevices;
import static org.bytedeco.libfreenect.global.freenect.freenect_set_log_level;
import static org.bytedeco.libfreenect.global.freenect.freenect_shutdown;
import static org.bytedeco.libfreenect.global.freenect.freenect_supported_subdevices;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import org.bytedeco.javacpp.DoublePointer;
import org.bytedeco.libfreenect.freenect_context;
import org.bytedeco.libfreenect.freenect_frame_mode;
import org.bytedeco.libfreenect.freenect_raw_tilt_state;
import org.bytedeco.libfreenect.freenect_reg_info;
import org.bytedeco.libfreenect.freenect_reg_pad_info;
import org.bytedeco.libfreenect.freenect_registration;
import org.bytedeco.libfreenect.freenect_zero_plane_info;
import org.junit.jupiter.api.Test;

public class LibfreenectTest {
    private static final double STANDARD_GRAVITY = 9.80665;

    @Test
    void enumeratesAndFindsEverySupportedVideoMode() {
        int modeCount = freenect_get_video_mode_count();
        assertThat(modeCount).isPositive();

        for (int index = 0; index < modeCount; index++) {
            try (freenect_frame_mode mode = freenect_get_video_mode(index);
                    freenect_frame_mode found =
                            freenect_find_video_mode(mode.resolution(), mode.video_format())) {
                assertValidFrameMode(mode);
                assertEquivalentFrameMode(found, mode);
            }
        }

        try (freenect_frame_mode unsupported =
                freenect_find_video_mode(FREENECT_RESOLUTION_DUMMY, FREENECT_VIDEO_DUMMY)) {
            assertThat(unsupported.is_valid()).isZero();
        }
    }

    @Test
    void enumeratesAndFindsEverySupportedDepthMode() {
        int modeCount = freenect_get_depth_mode_count();
        assertThat(modeCount).isPositive();

        for (int index = 0; index < modeCount; index++) {
            try (freenect_frame_mode mode = freenect_get_depth_mode(index);
                    freenect_frame_mode found =
                            freenect_find_depth_mode(mode.resolution(), mode.depth_format())) {
                assertValidFrameMode(mode);
                assertEquivalentFrameMode(found, mode);
            }
        }

        try (freenect_frame_mode unsupported =
                freenect_find_depth_mode(FREENECT_RESOLUTION_DUMMY, FREENECT_DEPTH_DUMMY)) {
            assertThat(unsupported.is_valid()).isZero();
        }
    }

    @Test
    void convertsRawTiltAndAccelerationReadingsAcrossJavaBindings() {
        try (freenect_raw_tilt_state state = new freenect_raw_tilt_state()) {
            state.accelerometer_x((short) 819)
                    .accelerometer_y((short) -819)
                    .accelerometer_z((short) 0)
                    .tilt_angle((byte) -20)
                    .tilt_status(TILT_STATUS_MOVING);

            assertThat(freenect_get_tilt_degs(state)).isEqualTo(-10.0);
            assertThat(freenect_get_tilt_status(state)).isEqualTo(TILT_STATUS_MOVING);

            double[] x = new double[1];
            double[] y = new double[1];
            double[] z = new double[1];
            freenect_get_mks_accel(state, x, y, z);
            assertAcceleration(x[0], y[0], z[0]);

            DoubleBuffer xBuffer = newNativeDoubleBuffer();
            DoubleBuffer yBuffer = newNativeDoubleBuffer();
            DoubleBuffer zBuffer = newNativeDoubleBuffer();
            freenect_get_mks_accel(state, xBuffer, yBuffer, zBuffer);
            assertAcceleration(xBuffer.get(0), yBuffer.get(0), zBuffer.get(0));

            try (DoublePointer xPointer = new DoublePointer(1);
                    DoublePointer yPointer = new DoublePointer(1);
                    DoublePointer zPointer = new DoublePointer(1)) {
                freenect_get_mks_accel(state, xPointer, yPointer, zPointer);
                assertAcceleration(xPointer.get(0), yPointer.get(0), zPointer.get(0));
            }
        }
    }

    @Test
    void storesRegistrationCalibrationInNativeStructures() {
        try (freenect_registration registration = new freenect_registration()) {
            freenect_reg_info registrationInfo = registration.reg_info();
            registrationInfo
                    .dx_center(320)
                    .ax(12)
                    .dy_start(-7)
                    .rollout_size(64)
                    .back_comp1(3);

            freenect_reg_pad_info paddingInfo = registration.reg_pad_info();
            paddingInfo.start_lines((short) 2).end_lines((short) 3).cropping_lines((short) 4);

            freenect_zero_plane_info zeroPlaneInfo = registration.zero_plane_info();
            zeroPlaneInfo
                    .dcmos_emitter_dist(7.5f)
                    .dcmos_rcmos_dist(2.5f)
                    .reference_distance(120.0f)
                    .reference_pixel_size(0.105f);

            registration.const_shift(200.5);

            assertThat(registration.reg_info().dx_center()).isEqualTo(320);
            assertThat(registration.reg_info().ax()).isEqualTo(12);
            assertThat(registration.reg_info().dy_start()).isEqualTo(-7);
            assertThat(registration.reg_info().rollout_size()).isEqualTo(64);
            assertThat(registration.reg_info().back_comp1()).isEqualTo(3);
            assertThat(registration.reg_pad_info().start_lines()).isEqualTo((short) 2);
            assertThat(registration.reg_pad_info().end_lines()).isEqualTo((short) 3);
            assertThat(registration.reg_pad_info().cropping_lines()).isEqualTo((short) 4);
            assertThat(registration.zero_plane_info().dcmos_emitter_dist()).isEqualTo(7.5f);
            assertThat(registration.zero_plane_info().dcmos_rcmos_dist()).isEqualTo(2.5f);
            assertThat(registration.zero_plane_info().reference_distance()).isEqualTo(120.0f);
            assertThat(registration.zero_plane_info().reference_pixel_size()).isEqualTo(0.105f);
            assertThat(registration.const_shift()).isEqualTo(200.5);
        }
    }

    @Test
    void initializesContextAndConfiguresDeviceEnumeration() {
        try (freenect_context context = new freenect_context()) {
            assertThat(freenect_init(context, null)).isZero();
            try {
                freenect_set_log_level(context, FREENECT_LOG_WARNING);
                assertThat(freenect_supported_subdevices() & FREENECT_DEVICE_CAMERA).isNotZero();

                freenect_select_subdevices(context, FREENECT_DEVICE_CAMERA);
                assertThat(freenect_enabled_subdevices(context)).isEqualTo(FREENECT_DEVICE_CAMERA);
                assertThat(freenect_num_devices(context)).isNotNegative();
            } finally {
                assertThat(freenect_shutdown(context)).isZero();
            }
        }
    }

    private static void assertValidFrameMode(freenect_frame_mode mode) {
        int width = Short.toUnsignedInt(mode.width());
        int height = Short.toUnsignedInt(mode.height());
        int dataBits = Byte.toUnsignedInt(mode.data_bits_per_pixel());
        int paddingBits = Byte.toUnsignedInt(mode.padding_bits_per_pixel());

        assertThat(mode.is_valid()).isNotZero();
        assertThat(width).isPositive();
        assertThat(height).isPositive();
        assertThat(dataBits).isPositive();
        assertThat(Byte.toUnsignedInt(mode.framerate())).isPositive();
        assertThat(mode.bytes()).isEqualTo(width * height * (dataBits + paddingBits) / Byte.SIZE);
    }

    private static void assertEquivalentFrameMode(
            freenect_frame_mode actual, freenect_frame_mode expected) {
        assertThat(actual.is_valid()).isNotZero();
        assertThat(actual.reserved()).isEqualTo(expected.reserved());
        assertThat(actual.resolution()).isEqualTo(expected.resolution());
        assertThat(actual.dummy()).isEqualTo(expected.dummy());
        assertThat(actual.bytes()).isEqualTo(expected.bytes());
        assertThat(actual.width()).isEqualTo(expected.width());
        assertThat(actual.height()).isEqualTo(expected.height());
        assertThat(actual.data_bits_per_pixel()).isEqualTo(expected.data_bits_per_pixel());
        assertThat(actual.padding_bits_per_pixel()).isEqualTo(expected.padding_bits_per_pixel());
        assertThat(actual.framerate()).isEqualTo(expected.framerate());
    }

    private static DoubleBuffer newNativeDoubleBuffer() {
        return ByteBuffer.allocateDirect(Double.BYTES)
                .order(ByteOrder.nativeOrder())
                .asDoubleBuffer();
    }

    private static void assertAcceleration(double x, double y, double z) {
        assertThat(x).isCloseTo(STANDARD_GRAVITY, within(0.000_001));
        assertThat(y).isCloseTo(-STANDARD_GRAVITY, within(0.000_001));
        assertThat(z).isZero();
    }
}
