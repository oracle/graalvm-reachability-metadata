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
import static org.bytedeco.librealsense2.global.realsense2.RS2_PRODUCT_LINE_DEPTH;
import static org.bytedeco.librealsense2.global.realsense2.RS2_STREAM_DEPTH;
import static org.bytedeco.librealsense2.global.realsense2.rs2_config_can_resolve;
import static org.bytedeco.librealsense2.global.realsense2.rs2_config_enable_device;
import static org.bytedeco.librealsense2.global.realsense2.rs2_config_enable_stream;
import static org.bytedeco.librealsense2.global.realsense2.rs2_create_config;
import static org.bytedeco.librealsense2.global.realsense2.rs2_create_context;
import static org.bytedeco.librealsense2.global.realsense2.rs2_create_decimation_filter_block;
import static org.bytedeco.librealsense2.global.realsense2.rs2_create_frame_queue;
import static org.bytedeco.librealsense2.global.realsense2.rs2_create_pipeline;
import static org.bytedeco.librealsense2.global.realsense2.rs2_delete_config;
import static org.bytedeco.librealsense2.global.realsense2.rs2_delete_context;
import static org.bytedeco.librealsense2.global.realsense2.rs2_delete_device_list;
import static org.bytedeco.librealsense2.global.realsense2.rs2_delete_frame_queue;
import static org.bytedeco.librealsense2.global.realsense2.rs2_delete_pipeline;
import static org.bytedeco.librealsense2.global.realsense2.rs2_delete_processing_block;
import static org.bytedeco.librealsense2.global.realsense2.rs2_deproject_pixel_to_point;
import static org.bytedeco.librealsense2.global.realsense2.rs2_frame_queue_size;
import static org.bytedeco.librealsense2.global.realsense2.rs2_free_error;
import static org.bytedeco.librealsense2.global.realsense2.rs2_get_api_version;
import static org.bytedeco.librealsense2.global.realsense2.rs2_get_device_count;
import static org.bytedeco.librealsense2.global.realsense2.rs2_get_error_message;
import static org.bytedeco.librealsense2.global.realsense2.rs2_get_failed_args;
import static org.bytedeco.librealsense2.global.realsense2.rs2_get_failed_function;
import static org.bytedeco.librealsense2.global.realsense2.rs2_get_librealsense_exception_type;
import static org.bytedeco.librealsense2.global.realsense2.rs2_is_processing_block_extendable_to;
import static org.bytedeco.librealsense2.global.realsense2.rs2_log;
import static org.bytedeco.librealsense2.global.realsense2.rs2_log_to_file;
import static org.bytedeco.librealsense2.global.realsense2.rs2_project_point_to_pixel;
import static org.bytedeco.librealsense2.global.realsense2.rs2_query_devices;
import static org.bytedeco.librealsense2.global.realsense2.rs2_query_devices_ex;
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
import org.bytedeco.librealsense2.rs2_device_list;
import org.bytedeco.librealsense2.rs2_error;
import org.bytedeco.librealsense2.rs2_extrinsics;
import org.bytedeco.librealsense2.rs2_frame_queue;
import org.bytedeco.librealsense2.rs2_intrinsics;
import org.bytedeco.librealsense2.rs2_pipeline;
import org.bytedeco.librealsense2.rs2_processing_block;

public class Librealsense2Test {
    @Test
    void reportsMatchingNativeApiVersion() {
        rs2_error error = new rs2_error();

        int nativeApiVersion = rs2_get_api_version(error);

        assertNoError(error);
        assertThat(nativeApiVersion).isEqualTo(RS2_API_VERSION);
    }

    @Test
    void enumeratesDevicesWithoutRequiringCameraHardware() {
        rs2_error error = new rs2_error();
        rs2_context context = null;
        rs2_device_list allDevices = null;
        rs2_device_list depthDevices = null;

        try {
            context = rs2_create_context(RS2_API_VERSION, error);
            assertNoError(error);
            assertThat(hasAddress(context)).isTrue();

            allDevices = rs2_query_devices(context, error);
            assertNoError(error);
            assertThat(hasAddress(allDevices)).isTrue();
            int allDeviceCount = rs2_get_device_count(allDevices, error);
            assertNoError(error);

            depthDevices = rs2_query_devices_ex(context, RS2_PRODUCT_LINE_DEPTH, error);
            assertNoError(error);
            assertThat(hasAddress(depthDevices)).isTrue();
            int depthDeviceCount = rs2_get_device_count(depthDevices, error);
            assertNoError(error);

            assertThat(allDeviceCount).isNotNegative();
            assertThat(depthDeviceCount).isBetween(0, allDeviceCount);
        } finally {
            if (hasAddress(depthDevices)) {
                rs2_delete_device_list(depthDevices);
            }
            if (hasAddress(allDevices)) {
                rs2_delete_device_list(allDevices);
            }
            if (hasAddress(context)) {
                rs2_delete_context(context);
            }
        }
    }

    @Test
    void configuresPipelineForUnavailableDeviceWithoutStartingCapture() {
        rs2_error error = new rs2_error();
        rs2_context context = null;
        rs2_pipeline pipeline = null;
        rs2_config config = null;

        try {
            context = rs2_create_context(RS2_API_VERSION, error);
            assertNoError(error);
            assertThat(hasAddress(context)).isTrue();
            pipeline = rs2_create_pipeline(context, error);
            assertNoError(error);
            assertThat(hasAddress(pipeline)).isTrue();
            config = rs2_create_config(error);
            assertNoError(error);
            assertThat(hasAddress(config)).isTrue();

            rs2_config_enable_stream(config, RS2_STREAM_DEPTH, -1, 640, 480, RS2_FORMAT_Z16, 30, error);
            assertNoError(error);
            rs2_config_enable_device(config, "forge-no-such-realsense-device", error);
            assertNoError(error);

            int canResolve = rs2_config_can_resolve(config, pipeline, error);

            assertNoError(error);
            assertThat(canResolve).isZero();
        } finally {
            if (hasAddress(config)) {
                rs2_delete_config(config);
            }
            if (hasAddress(pipeline)) {
                rs2_delete_pipeline(pipeline);
            }
            if (hasAddress(context)) {
                rs2_delete_context(context);
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
