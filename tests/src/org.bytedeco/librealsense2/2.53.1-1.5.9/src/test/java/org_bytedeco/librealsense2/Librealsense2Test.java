/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bytedeco.librealsense2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.api.Assertions.within;
import static org.bytedeco.librealsense2.global.realsense2.RS2_API_VERSION;
import static org.bytedeco.librealsense2.global.realsense2.RS2_DISTORTION_NONE;
import static org.bytedeco.librealsense2.global.realsense2.RS2_EXCEPTION_TYPE_INVALID_VALUE;
import static org.bytedeco.librealsense2.global.realsense2.RS2_EXTENSION_DECIMATION_FILTER;
import static org.bytedeco.librealsense2.global.realsense2.RS2_FORMAT_Z16;
import static org.bytedeco.librealsense2.global.realsense2.RS2_LOG_SEVERITY_DEBUG;
import static org.bytedeco.librealsense2.global.realsense2.RS2_LOG_SEVERITY_INFO;
import static org.bytedeco.librealsense2.global.realsense2.RS2_STREAM_DEPTH;
import static org.bytedeco.librealsense2.global.realsense2.rs2_config_enable_device;
import static org.bytedeco.librealsense2.global.realsense2.rs2_config_enable_stream;
import static org.bytedeco.librealsense2.global.realsense2.rs2_create_config;
import static org.bytedeco.librealsense2.global.realsense2.rs2_create_context;
import static org.bytedeco.librealsense2.global.realsense2.rs2_create_decimation_filter_block;
import static org.bytedeco.librealsense2.global.realsense2.rs2_create_frame_queue;
import static org.bytedeco.librealsense2.global.realsense2.rs2_delete_config;
import static org.bytedeco.librealsense2.global.realsense2.rs2_delete_context;
import static org.bytedeco.librealsense2.global.realsense2.rs2_delete_frame_queue;
import static org.bytedeco.librealsense2.global.realsense2.rs2_delete_processing_block;
import static org.bytedeco.librealsense2.global.realsense2.rs2_deproject_pixel_to_point;
import static org.bytedeco.librealsense2.global.realsense2.rs2_exception_type_to_string;
import static org.bytedeco.librealsense2.global.realsense2.rs2_extension_to_string;
import static org.bytedeco.librealsense2.global.realsense2.rs2_frame_queue_size;
import static org.bytedeco.librealsense2.global.realsense2.rs2_free_error;
import static org.bytedeco.librealsense2.global.realsense2.rs2_get_api_version;
import static org.bytedeco.librealsense2.global.realsense2.rs2_get_error_message;
import static org.bytedeco.librealsense2.global.realsense2.rs2_get_failed_args;
import static org.bytedeco.librealsense2.global.realsense2.rs2_get_failed_function;
import static org.bytedeco.librealsense2.global.realsense2.rs2_get_librealsense_exception_type;
import static org.bytedeco.librealsense2.global.realsense2.rs2_is_processing_block_extendable_to;
import static org.bytedeco.librealsense2.global.realsense2.rs2_log;
import static org.bytedeco.librealsense2.global.realsense2.rs2_log_severity_to_string;
import static org.bytedeco.librealsense2.global.realsense2.rs2_log_to_file;
import static org.bytedeco.librealsense2.global.realsense2.rs2_project_point_to_pixel;
import static org.bytedeco.librealsense2.global.realsense2.rs2_reset_logger;
import static org.bytedeco.librealsense2.global.realsense2.rs2_transform_point_to_point;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.bytedeco.javacpp.Pointer;
import org.bytedeco.librealsense2.rs2_config;
import org.bytedeco.librealsense2.rs2_context;
import org.bytedeco.librealsense2.rs2_error;
import org.bytedeco.librealsense2.rs2_extrinsics;
import org.bytedeco.librealsense2.rs2_frame_queue;
import org.bytedeco.librealsense2.rs2_intrinsics;
import org.bytedeco.librealsense2.rs2_pose;
import org.bytedeco.librealsense2.rs2_processing_block;
import org.bytedeco.librealsense2.rs2_quaternion;
import org.bytedeco.librealsense2.rs2_vector;

public class Librealsense2Test {
    @Test
    void reportsMatchingNativeApiVersion() {
        rs2_error error = new rs2_error();

        int nativeApiVersion = rs2_get_api_version(error);

        assertNoError(error);
        assertThat(nativeApiVersion).isEqualTo(RS2_API_VERSION);
    }

    @Test
    void translatesNativeEnumValuesToStrings() {
        assertThat(rs2_exception_type_to_string(RS2_EXCEPTION_TYPE_INVALID_VALUE).getString())
                .containsIgnoringCase("invalid")
                .containsIgnoringCase("value");
        assertThat(rs2_extension_to_string(RS2_EXTENSION_DECIMATION_FILTER).getString())
                .containsIgnoringCase("decimation")
                .containsIgnoringCase("filter");
        assertThat(rs2_log_severity_to_string(RS2_LOG_SEVERITY_INFO).getString())
                .isEqualToIgnoringCase("info");
    }

    @Test
    void configuresStreamForSelectedDeviceWithoutStartingCapture() {
        rs2_error error = new rs2_error();
        rs2_config config = null;

        try {
            config = rs2_create_config(error);
            assertNoError(error);
            assertThat(hasAddress(config)).isTrue();

            rs2_config_enable_stream(config, RS2_STREAM_DEPTH, -1, 640, 480, RS2_FORMAT_Z16, 30, error);
            assertNoError(error);
            rs2_config_enable_device(config, "forge-no-such-realsense-device", error);
            assertNoError(error);
        } finally {
            if (hasAddress(config)) {
                rs2_delete_config(config);
            }
        }
    }

    @Test
    void projectsDeprojectsAndTransformsCoordinates() {
        try (rs2_intrinsics intrinsics = new rs2_intrinsics();
                rs2_extrinsics extrinsics = new rs2_extrinsics()) {
            intrinsics
                    .width(640)
                    .height(480)
                    .ppx(320.0f)
                    .ppy(240.0f)
                    .fx(100.0f)
                    .fy(100.0f)
                    .model(RS2_DISTORTION_NONE);
            for (int index = 0; index < 5; index++) {
                intrinsics.coeffs(index, 0.0f);
            }

            float[] point = {1.0f, 2.0f, 4.0f};
            float[] pixel = new float[2];
            rs2_project_point_to_pixel(pixel, intrinsics, point);

            assertThat(pixel[0]).isCloseTo(345.0f, within(0.0001f));
            assertThat(pixel[1]).isCloseTo(290.0f, within(0.0001f));

            float[] deprojected = new float[3];
            rs2_deproject_pixel_to_point(deprojected, intrinsics, pixel, 4.0f);

            assertThat(deprojected[0]).isCloseTo(point[0], within(0.0001f));
            assertThat(deprojected[1]).isCloseTo(point[1], within(0.0001f));
            assertThat(deprojected[2]).isCloseTo(point[2], within(0.0001f));

            float[] identityRotation = {1.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 1.0f};
            for (int index = 0; index < identityRotation.length; index++) {
                extrinsics.rotation(index, identityRotation[index]);
            }
            extrinsics.translation(0, 0.5f).translation(1, -1.0f).translation(2, 2.0f);

            float[] transformed = new float[3];
            rs2_transform_point_to_point(transformed, extrinsics, point);

            assertThat(transformed[0]).isCloseTo(1.5f, within(0.0001f));
            assertThat(transformed[1]).isCloseTo(1.0f, within(0.0001f));
            assertThat(transformed[2]).isCloseTo(6.0f, within(0.0001f));
        }
    }

    @Test
    void representsTrackedPoseWithMotionAndConfidence() {
        try (rs2_pose pose = new rs2_pose();
                rs2_vector translation = new rs2_vector();
                rs2_vector velocity = new rs2_vector();
                rs2_vector angularVelocity = new rs2_vector();
                rs2_quaternion rotation = new rs2_quaternion()) {
            translation.x(1.25f).y(-2.5f).z(3.75f);
            velocity.x(0.5f).y(1.5f).z(-0.25f);
            angularVelocity.x(-0.1f).y(0.2f).z(0.3f);
            rotation.x(0.0f).y(0.3826834f).z(0.0f).w(0.9238795f);

            pose.translation(translation)
                    .velocity(velocity)
                    .angular_velocity(angularVelocity)
                    .rotation(rotation)
                    .tracker_confidence(3)
                    .mapper_confidence(2);

            assertThat(pose.translation().x()).isEqualTo(1.25f);
            assertThat(pose.translation().y()).isEqualTo(-2.5f);
            assertThat(pose.translation().z()).isEqualTo(3.75f);
            assertThat(pose.velocity().x()).isEqualTo(0.5f);
            assertThat(pose.velocity().y()).isEqualTo(1.5f);
            assertThat(pose.velocity().z()).isEqualTo(-0.25f);
            assertThat(pose.angular_velocity().x()).isEqualTo(-0.1f);
            assertThat(pose.angular_velocity().y()).isEqualTo(0.2f);
            assertThat(pose.angular_velocity().z()).isEqualTo(0.3f);
            assertThat(pose.rotation().x()).isZero();
            assertThat(pose.rotation().y()).isEqualTo(0.3826834f);
            assertThat(pose.rotation().z()).isZero();
            assertThat(pose.rotation().w()).isEqualTo(0.9238795f);
            assertThat(pose.tracker_confidence()).isEqualTo(3);
            assertThat(pose.mapper_confidence()).isEqualTo(2);
        }
    }

    @Test
    void createsDeviceIndependentProcessingObjects() {
        rs2_error error = new rs2_error();
        rs2_processing_block decimationFilter = null;
        rs2_frame_queue frameQueue = null;

        try {
            decimationFilter = rs2_create_decimation_filter_block(error);
            assertNoError(error);
            assertThat(hasAddress(decimationFilter)).isTrue();

            int isDecimationFilter =
                    rs2_is_processing_block_extendable_to(
                            decimationFilter, RS2_EXTENSION_DECIMATION_FILTER, error);
            assertNoError(error);
            assertThat(isDecimationFilter).isEqualTo(1);

            frameQueue = rs2_create_frame_queue(2, error);
            assertNoError(error);
            assertThat(hasAddress(frameQueue)).isTrue();

            int queueSize = rs2_frame_queue_size(frameQueue, error);
            assertNoError(error);
            assertThat(queueSize).isZero();
        } finally {
            if (hasAddress(frameQueue)) {
                rs2_delete_frame_queue(frameQueue);
            }
            if (hasAddress(decimationFilter)) {
                rs2_delete_processing_block(decimationFilter);
            }
        }
    }

    @Test
    void writesApplicationMessagesToConfiguredLogFile(@TempDir Path temporaryDirectory) throws IOException {
        rs2_error error = new rs2_error();
        Path logFile = temporaryDirectory.resolve("librealsense.log");
        String message = "application message from the librealsense test";

        try {
            rs2_log_to_file(RS2_LOG_SEVERITY_DEBUG, logFile.toString(), error);
            assertNoError(error);
            rs2_log(RS2_LOG_SEVERITY_INFO, message, error);
            assertNoError(error);
        } finally {
            rs2_error resetError = new rs2_error();
            rs2_reset_logger(resetError);
            assertNoError(resetError);
        }

        assertThat(logFile).exists();
        assertThat(Files.readString(logFile)).contains(message);
    }

    @Test
    void reportsIncompatibleApiVersionAsNativeError() {
        rs2_error error = new rs2_error();
        rs2_context context = rs2_create_context(0, error);

        try {
            assertThat(hasAddress(context)).isFalse();
            assertThat(hasAddress(error)).isTrue();
            assertThat(rs2_get_librealsense_exception_type(error))
                    .isEqualTo(RS2_EXCEPTION_TYPE_INVALID_VALUE);
            assertThat(rs2_get_failed_function(error).getString()).isEqualTo("rs2_create_context");
            assertThat(rs2_get_failed_args(error).getString()).contains("api_version:0");
            assertThat(rs2_get_error_message(error).getString()).contains("API version mismatch");
        } finally {
            if (hasAddress(context)) {
                rs2_delete_context(context);
            }
            if (hasAddress(error)) {
                rs2_free_error(error);
            }
        }
    }

    private static boolean hasAddress(Pointer pointer) {
        return pointer != null && !pointer.isNull();
    }

    private static void assertNoError(rs2_error error) {
        if (hasAddress(error)) {
            String message = rs2_get_error_message(error).getString();
            rs2_free_error(error);
            fail(message);
        }
    }
}
