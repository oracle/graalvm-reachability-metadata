/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bytedeco.libdc1394;

import static org.assertj.core.api.Assertions.assertThat;
import static org.bytedeco.libdc1394.global.dc1394.DC1394_BAYER_METHOD_BILINEAR;
import static org.bytedeco.libdc1394.global.dc1394.DC1394_BAYER_METHOD_NEAREST;
import static org.bytedeco.libdc1394.global.dc1394.DC1394_COLOR_CODING_MONO8;
import static org.bytedeco.libdc1394.global.dc1394.DC1394_COLOR_CODING_RGB16;
import static org.bytedeco.libdc1394.global.dc1394.DC1394_COLOR_CODING_RGB8;
import static org.bytedeco.libdc1394.global.dc1394.DC1394_COLOR_FILTER_BGGR;
import static org.bytedeco.libdc1394.global.dc1394.DC1394_FALSE;
import static org.bytedeco.libdc1394.global.dc1394.DC1394_FEATURE_GAIN;
import static org.bytedeco.libdc1394.global.dc1394.DC1394_FRAMERATE_30;
import static org.bytedeco.libdc1394.global.dc1394.DC1394_SUCCESS;
import static org.bytedeco.libdc1394.global.dc1394.DC1394_TRUE;
import static org.bytedeco.libdc1394.global.dc1394.DC1394_VIDEO_MODE_640x480_RGB8;
import static org.bytedeco.libdc1394.global.dc1394.DC1394_VIDEO_MODE_FORMAT7_0;
import static org.bytedeco.libdc1394.global.dc1394.dc1394_bayer_decoding_16bit;
import static org.bytedeco.libdc1394.global.dc1394.dc1394_bayer_decoding_8bit;
import static org.bytedeco.libdc1394.global.dc1394.dc1394_camera_enumerate;
import static org.bytedeco.libdc1394.global.dc1394.dc1394_camera_free_list;
import static org.bytedeco.libdc1394.global.dc1394.dc1394_checksum_crc16;
import static org.bytedeco.libdc1394.global.dc1394.dc1394_convert_frames;
import static org.bytedeco.libdc1394.global.dc1394.dc1394_deinterlace_stereo;
import static org.bytedeco.libdc1394.global.dc1394.dc1394_error_get_string;
import static org.bytedeco.libdc1394.global.dc1394.dc1394_feature_get_string;
import static org.bytedeco.libdc1394.global.dc1394.dc1394_framerate_as_float;
import static org.bytedeco.libdc1394.global.dc1394.dc1394_free;
import static org.bytedeco.libdc1394.global.dc1394.dc1394_get_color_coding_bit_size;
import static org.bytedeco.libdc1394.global.dc1394.dc1394_get_color_coding_data_depth;
import static org.bytedeco.libdc1394.global.dc1394.dc1394_get_color_coding_from_video_mode;
import static org.bytedeco.libdc1394.global.dc1394.dc1394_get_image_size_from_video_mode;
import static org.bytedeco.libdc1394.global.dc1394.dc1394_is_color;
import static org.bytedeco.libdc1394.global.dc1394.dc1394_is_same_camera;
import static org.bytedeco.libdc1394.global.dc1394.dc1394_is_video_mode_scalable;
import static org.bytedeco.libdc1394.global.dc1394.dc1394_new;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.libdc1394.dc1394_t;
import org.bytedeco.libdc1394.dc1394camera_id_t;
import org.bytedeco.libdc1394.dc1394camera_list_t;
import org.bytedeco.libdc1394.dc1394video_frame_t;
import org.junit.jupiter.api.Test;

public class Libdc1394Test {
    @Test
    void createsContextAndEnumeratesAvailableCameras() {
        dc1394_t context = dc1394_new();

        if (context == null || context.isNull()) {
            long contextAddress = context == null ? 0L : context.address();
            assertThat(contextAddress).isZero();
        } else {
            dc1394camera_list_t cameras = new dc1394camera_list_t((Pointer) null);
            try {
                assertThat(dc1394_camera_enumerate(context, cameras)).isEqualTo(DC1394_SUCCESS);
                assertThat(cameras).isNotNull();
                assertThat(cameras.isNull()).isFalse();
                assertThat(cameras.num()).isNotNegative();

                dc1394camera_id_t cameraIds = cameras.ids();
                if (cameras.num() == 0) {
                    assertThat(cameraIds == null || cameraIds.isNull()).isTrue();
                } else {
                    assertThat(cameraIds).isNotNull();
                    assertThat(cameraIds.isNull()).isFalse();
                    assertThat(cameraIds.guid()).isNotZero();
                }
            } finally {
                if (!cameras.isNull()) {
                    dc1394_camera_free_list(cameras);
                }
                dc1394_free(context);
            }
        }
    }

    @Test
    void convertsMonochromeFrameToRgbAndPreservesFrameInformation() {
        byte[] monochrome = {0, 64, (byte) 128, (byte) 255};
        byte[] converted = new byte[monochrome.length * 3];

        try (BytePointer inputImage = new BytePointer(monochrome);
                BytePointer outputImage = new BytePointer(converted.length);
                dc1394video_frame_t input = new dc1394video_frame_t();
                dc1394video_frame_t output = new dc1394video_frame_t()) {
            input.image(inputImage)
                    .size(0, 2)
                    .size(1, 2)
                    ._position(0, 3)
                    ._position(1, 5)
                    .color_coding(DC1394_COLOR_CODING_MONO8)
                    .color_filter(DC1394_COLOR_FILTER_BGGR)
                    .data_depth(8)
                    .video_mode(DC1394_VIDEO_MODE_FORMAT7_0)
                    .image_bytes(monochrome.length)
                    .total_bytes(monochrome.length)
                    .padding_bytes(0)
                    .packet_size(4)
                    .packets_per_frame(1)
                    .timestamp(123456L)
                    .frames_behind(2)
                    .id(7);
            output.image(outputImage)
                    .allocated_image_bytes(converted.length)
                    .color_coding(DC1394_COLOR_CODING_RGB8);

            assertThat(dc1394_convert_frames(input, output)).isEqualTo(DC1394_SUCCESS);
            outputImage.get(converted);

            assertThat(converted)
                    .containsExactly(
                            (byte) 0,
                            (byte) 0,
                            (byte) 0,
                            (byte) 64,
                            (byte) 64,
                            (byte) 64,
                            (byte) 128,
                            (byte) 128,
                            (byte) 128,
                            (byte) 255,
                            (byte) 255,
                            (byte) 255);
            assertThat(output.size(0)).isEqualTo(2);
            assertThat(output.size(1)).isEqualTo(2);
            assertThat(output._position(0)).isEqualTo(3);
            assertThat(output._position(1)).isEqualTo(5);
            assertThat(output.color_coding()).isEqualTo(DC1394_COLOR_CODING_RGB8);
            assertThat(output.color_filter()).isEqualTo(DC1394_COLOR_FILTER_BGGR);
            assertThat(output.data_depth()).isEqualTo(8);
            assertThat(output.image_bytes()).isEqualTo(converted.length);
            assertThat(output.total_bytes()).isEqualTo(converted.length);
            assertThat(output.timestamp()).isEqualTo(123456L);
            assertThat(output.frames_behind()).isEqualTo(2);
            assertThat(output.id()).isEqualTo(7);
        }
    }

    @Test
    void deinterlacesStereoPixelsIntoTwoImages() {
        byte[] interlaced = {0, 1, 2, 3, 4, 5, 6, 7};
        byte[] deinterlaced = new byte[interlaced.length];

        assertThat(dc1394_deinterlace_stereo(interlaced, deinterlaced, 4, 2))
                .isEqualTo(DC1394_SUCCESS);
        assertThat(deinterlaced)
                .containsExactly(
                        (byte) 0,
                        (byte) 2,
                        (byte) 4,
                        (byte) 6,
                        (byte) 1,
                        (byte) 3,
                        (byte) 5,
                        (byte) 7);
    }

    @Test
    void decodesEightAndSixteenBitBayerImages() {
        int width = 6;
        int height = 6;
        byte[] bayer8 = new byte[width * height];
        byte[] rgb8 = new byte[width * height * 3];
        Arrays.fill(bayer8, (byte) 90);

        assertThat(dc1394_bayer_decoding_8bit(
                        bayer8, rgb8, width, height, DC1394_COLOR_FILTER_BGGR, DC1394_BAYER_METHOD_BILINEAR))
                .isEqualTo(DC1394_SUCCESS);
        assertThat(Arrays.copyOfRange(rgb8, 3 * (width + 1), 3 * (width + 1) + 3))
                .containsExactly((byte) 90, (byte) 90, (byte) 90);

        short[] bayer16 = new short[width * height];
        short[] rgb16 = new short[width * height * 3];
        Arrays.fill(bayer16, (short) 1200);

        assertThat(dc1394_bayer_decoding_16bit(
                        bayer16,
                        rgb16,
                        width,
                        height,
                        DC1394_COLOR_FILTER_BGGR,
                        DC1394_BAYER_METHOD_NEAREST,
                        12))
                .isEqualTo(DC1394_SUCCESS);
        assertThat(Arrays.copyOfRange(rgb16, 3 * (width + 1), 3 * (width + 1) + 3))
                .containsExactly((short) 1200, (short) 1200, (short) 1200);
    }

    @Test
    void exposesCameraAndVideoUtilityInformation() {
        float[] frameRate = new float[1];
        int[] dataDepth = new int[1];
        int[] bitSize = new int[1];
        int[] isColor = new int[1];
        int[] width = new int[1];
        int[] height = new int[1];
        int[] colorCoding = new int[1];

        assertThat(dc1394_framerate_as_float(DC1394_FRAMERATE_30, frameRate)).isEqualTo(DC1394_SUCCESS);
        assertThat(frameRate[0]).isEqualTo(30.0f);
        assertThat(dc1394_get_color_coding_data_depth(DC1394_COLOR_CODING_RGB16, dataDepth))
                .isEqualTo(DC1394_SUCCESS);
        assertThat(dataDepth[0]).isEqualTo(16);
        assertThat(dc1394_get_color_coding_bit_size(DC1394_COLOR_CODING_RGB16, bitSize))
                .isEqualTo(DC1394_SUCCESS);
        assertThat(bitSize[0]).isEqualTo(48);
        assertThat(dc1394_is_color(DC1394_COLOR_CODING_RGB8, isColor)).isEqualTo(DC1394_SUCCESS);
        assertThat(isColor[0]).isEqualTo(DC1394_TRUE);
        assertThat(dc1394_is_color(DC1394_COLOR_CODING_MONO8, isColor)).isEqualTo(DC1394_SUCCESS);
        assertThat(isColor[0]).isEqualTo(DC1394_FALSE);
        assertThat(dc1394_get_image_size_from_video_mode(null, DC1394_VIDEO_MODE_640x480_RGB8, width, height))
                .isEqualTo(DC1394_SUCCESS);
        assertThat(width[0]).isEqualTo(640);
        assertThat(height[0]).isEqualTo(480);
        assertThat(dc1394_get_color_coding_from_video_mode(
                        null, DC1394_VIDEO_MODE_640x480_RGB8, colorCoding))
                .isEqualTo(DC1394_SUCCESS);
        assertThat(colorCoding[0]).isEqualTo(DC1394_COLOR_CODING_RGB8);
        assertThat(dc1394_is_video_mode_scalable(DC1394_VIDEO_MODE_FORMAT7_0)).isEqualTo(DC1394_TRUE);
        assertThat(dc1394_error_get_string(DC1394_SUCCESS).getString()).isEqualTo("Success");
        assertThat(dc1394_feature_get_string(DC1394_FEATURE_GAIN).getString()).isEqualTo("Gain");

        byte[] checksumInput = "123456789".getBytes(StandardCharsets.US_ASCII);
        assertThat(dc1394_checksum_crc16(checksumInput, checksumInput.length) & 0xffff).isEqualTo(0x31C3);
    }

    @Test
    void comparesCameraIdentifiersByGuidAndUnit() {
        try (dc1394camera_id_t first = new dc1394camera_id_t();
                dc1394camera_id_t second = new dc1394camera_id_t()) {
            first.guid(0x0123456789ABCDEFL).unit((short) 2);
            second.guid(0x0123456789ABCDEFL).unit((short) 2);

            assertThat(first.guid()).isEqualTo(0x0123456789ABCDEFL);
            assertThat(first.unit()).isEqualTo((short) 2);
            assertThat(dc1394_is_same_camera(first, second)).isEqualTo(DC1394_TRUE);

            second.unit((short) 3);
            assertThat(dc1394_is_same_camera(first, second)).isEqualTo(DC1394_FALSE);
        }
    }
}
