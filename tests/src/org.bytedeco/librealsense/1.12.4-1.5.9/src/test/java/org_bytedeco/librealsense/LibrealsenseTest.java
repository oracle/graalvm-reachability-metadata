/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bytedeco.librealsense;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.bytedeco.librealsense.global.RealSense.RS_API_MAJOR_VERSION;
import static org.bytedeco.librealsense.global.RealSense.RS_API_MINOR_VERSION;
import static org.bytedeco.librealsense.global.RealSense.RS_API_PATCH_VERSION;
import static org.bytedeco.librealsense.global.RealSense.RS_API_VERSION;
import static org.bytedeco.librealsense.global.RealSense.RS_API_VERSION_STR;
import static org.bytedeco.librealsense.global.RealSense.RS_DISTORTION_NONE;
import static org.bytedeco.librealsense.global.RealSense.RS_EVENT_IMU_ACCEL;
import static org.bytedeco.librealsense.global.RealSense.RS_FORMAT_Z16;
import static org.bytedeco.librealsense.global.RealSense.RS_PRESET_BEST_QUALITY;
import static org.bytedeco.librealsense.global.RealSense.RS_SOURCE_VIDEO;
import static org.bytedeco.librealsense.global.RealSense.RS_STREAM_DEPTH;
import static org.bytedeco.librealsense.global.RealSense.rs_distortion_to_string;
import static org.bytedeco.librealsense.global.RealSense.rs_event_to_string;
import static org.bytedeco.librealsense.global.RealSense.rs_format_to_string;
import static org.bytedeco.librealsense.global.RealSense.rs_get_api_version;
import static org.bytedeco.librealsense.global.RealSense.rs_preset_to_string;
import static org.bytedeco.librealsense.global.RealSense.rs_source_to_string;
import static org.bytedeco.librealsense.global.RealSense.rs_stream_to_string;

import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.librealsense.context;
import org.bytedeco.librealsense.extrinsics;
import org.bytedeco.librealsense.float2;
import org.bytedeco.librealsense.float3;
import org.bytedeco.librealsense.intrinsics;
import org.bytedeco.librealsense.motion_data;
import org.bytedeco.librealsense.rs_error;
import org.bytedeco.librealsense.rs_timestamp_data;
import org.bytedeco.librealsense.timestamp_data;
import org.junit.jupiter.api.Test;

public class LibrealsenseTest {
    private static final float FLOAT_TOLERANCE = 0.0001f;

    @Test
    void loadsMatchingNativeApiAndReportsNoConnectedDevices() {
        int encodedVersion =
                RS_API_MAJOR_VERSION * 10_000 + RS_API_MINOR_VERSION * 100 + RS_API_PATCH_VERSION;
        String displayedVersion =
                "%d.%d.%d".formatted(RS_API_MAJOR_VERSION, RS_API_MINOR_VERSION, RS_API_PATCH_VERSION);

        assertThat(RS_API_VERSION).isEqualTo(encodedVersion);
        assertThat(RS_API_VERSION_STR).isEqualTo(displayedVersion);
        assertThat(rs_get_api_version((rs_error) null)).isEqualTo(encodedVersion);

        try (context realSenseContext = new context()) {
            assertThat(realSenseContext.isNull()).isFalse();
            assertThat(realSenseContext.get_device_count()).isZero();
        }
    }

    @Test
    void exposesNativeNamesForPublicEnumValues() {
        assertThat(readNativeString(rs_stream_to_string(RS_STREAM_DEPTH))).isEqualTo("DEPTH");
        assertThat(readNativeString(rs_format_to_string(RS_FORMAT_Z16))).isEqualTo("Z16");
        assertThat(readNativeString(rs_preset_to_string(RS_PRESET_BEST_QUALITY)))
                .isEqualTo("BEST_QUALITY");
        assertThat(readNativeString(rs_distortion_to_string(RS_DISTORTION_NONE))).isEqualTo("NONE");
        assertThat(readNativeString(rs_source_to_string(RS_SOURCE_VIDEO))).isEqualTo("VIDEO");
        assertThat(readNativeString(rs_event_to_string(RS_EVENT_IMU_ACCEL))).isEqualTo("IMU_ACCEL");
    }

    @Test
    void projectsAndDeprojectsUsingCameraIntrinsics() {
        try (intrinsics camera = new intrinsics(); float3 point = new float3()) {
            camera.width(640);
            camera.height(480);
            camera.ppx(319.5f);
            camera.ppy(239.5f);
            camera.fx(400.0f);
            camera.fy(400.0f);
            camera.model(RS_DISTORTION_NONE);
            for (int index = 0; index < 5; index++) {
                camera.coeffs(index, 0.0f);
            }
            point.x(1.0f);
            point.y(0.5f);
            point.z(2.0f);

            try (float2 pixel = camera.project(point);
                    float3 restoredPoint = camera.deproject(pixel, point.z());
                    float2 textureCoordinate = camera.pixel_to_texcoord(pixel);
                    float2 restoredPixel = camera.texcoord_to_pixel(textureCoordinate)) {
                assertThat(pixel.x()).isCloseTo(519.5f, within(FLOAT_TOLERANCE));
                assertThat(pixel.y()).isCloseTo(339.5f, within(FLOAT_TOLERANCE));
                assertThat(restoredPoint.x()).isCloseTo(point.x(), within(FLOAT_TOLERANCE));
                assertThat(restoredPoint.y()).isCloseTo(point.y(), within(FLOAT_TOLERANCE));
                assertThat(restoredPoint.z()).isCloseTo(point.z(), within(FLOAT_TOLERANCE));
                assertThat(textureCoordinate.x()).isCloseTo(0.8125f, within(FLOAT_TOLERANCE));
                assertThat(textureCoordinate.y())
                        .isCloseTo(0.7083333f, within(FLOAT_TOLERANCE));
                assertThat(restoredPixel.x()).isCloseTo(pixel.x(), within(FLOAT_TOLERANCE));
                assertThat(restoredPixel.y()).isCloseTo(pixel.y(), within(FLOAT_TOLERANCE));
                assertThat(camera.hfov()).isBetween(0.0f, 180.0f);
                assertThat(camera.vfov()).isBetween(0.0f, 180.0f);
            }
        }
    }

    @Test
    void transformsPointsUsingCameraExtrinsics() {
        try (extrinsics pose = new extrinsics(); float3 point = new float3()) {
            for (int index = 0; index < 9; index++) {
                pose.rotation(index, 0.0f);
            }
            pose.rotation(0, 1.0f);
            pose.rotation(4, 1.0f);
            pose.rotation(8, 1.0f);
            assertThat(pose.is_identity()).isTrue();

            pose.translation(0, 0.5f);
            pose.translation(1, -1.0f);
            pose.translation(2, 2.0f);
            point.x(1.0f);
            point.y(2.0f);
            point.z(3.0f);

            assertThat(pose.is_identity()).isFalse();
            try (float3 transformed = pose.transform(point)) {
                assertThat(transformed.x()).isCloseTo(1.5f, within(FLOAT_TOLERANCE));
                assertThat(transformed.y()).isCloseTo(1.0f, within(FLOAT_TOLERANCE));
                assertThat(transformed.z()).isCloseTo(5.0f, within(FLOAT_TOLERANCE));
            }
        }
    }

    @Test
    void preservesMotionSamplesAndTheirTimestamps() {
        try (timestamp_data eventTimestamp = new timestamp_data();
                motion_data sample = new motion_data()) {
            eventTimestamp.timestamp(1_234.5);
            eventTimestamp.source_id(RS_EVENT_IMU_ACCEL);
            eventTimestamp.frame_number(17L);
            sample.timestamp_data(eventTimestamp);
            sample.is_valid(1);
            sample.axes(0, 0.25f);
            sample.axes(1, -0.5f);
            sample.axes(2, 1.5f);

            rs_timestamp_data storedTimestamp = sample.timestamp_data();
            assertThat(storedTimestamp.timestamp()).isEqualTo(1_234.5);
            assertThat(storedTimestamp.source_id()).isEqualTo(RS_EVENT_IMU_ACCEL);
            assertThat(storedTimestamp.frame_number()).isEqualTo(17L);
            assertThat(sample.is_valid()).isEqualTo(1);
            assertThat(sample.axes(0)).isCloseTo(0.25f, within(FLOAT_TOLERANCE));
            assertThat(sample.axes(1)).isCloseTo(-0.5f, within(FLOAT_TOLERANCE));
            assertThat(sample.axes(2)).isCloseTo(1.5f, within(FLOAT_TOLERANCE));
        }
    }

    private static String readNativeString(BytePointer value) {
        assertThat(value).isNotNull();
        assertThat(value.isNull()).isFalse();
        return value.getString();
    }
}
