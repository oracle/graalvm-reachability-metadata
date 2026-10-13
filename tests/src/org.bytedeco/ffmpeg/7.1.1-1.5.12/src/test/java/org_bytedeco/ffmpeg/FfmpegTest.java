/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bytedeco.ffmpeg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_PNG;
import static org.bytedeco.ffmpeg.global.avcodec.av_packet_alloc;
import static org.bytedeco.ffmpeg.global.avcodec.av_packet_free;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_alloc_context3;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_find_decoder;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_find_encoder;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_free_context;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_open2;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_parameters_to_context;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_receive_frame;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_receive_packet;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_send_frame;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_send_packet;
import static org.bytedeco.ffmpeg.global.avformat.av_read_frame;
import static org.bytedeco.ffmpeg.global.avformat.avformat_close_input;
import static org.bytedeco.ffmpeg.global.avformat.avformat_find_stream_info;
import static org.bytedeco.ffmpeg.global.avformat.avformat_open_input;
import static org.bytedeco.ffmpeg.global.avfilter.av_buffersink_get_frame;
import static org.bytedeco.ffmpeg.global.avfilter.av_buffersrc_add_frame;
import static org.bytedeco.ffmpeg.global.avfilter.avfilter_get_by_name;
import static org.bytedeco.ffmpeg.global.avfilter.avfilter_graph_alloc;
import static org.bytedeco.ffmpeg.global.avfilter.avfilter_graph_config;
import static org.bytedeco.ffmpeg.global.avfilter.avfilter_graph_create_filter;
import static org.bytedeco.ffmpeg.global.avfilter.avfilter_graph_free;
import static org.bytedeco.ffmpeg.global.avfilter.avfilter_link;
import static org.bytedeco.ffmpeg.global.avutil.AVMEDIA_TYPE_VIDEO;
import static org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_BGR24;
import static org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_RGB24;
import static org.bytedeco.ffmpeg.global.avutil.AV_SAMPLE_FMT_FLT;
import static org.bytedeco.ffmpeg.global.avutil.AV_SAMPLE_FMT_S16;
import static org.bytedeco.ffmpeg.global.avutil.av_channel_layout_copy;
import static org.bytedeco.ffmpeg.global.avutil.av_channel_layout_default;
import static org.bytedeco.ffmpeg.global.avutil.av_channel_layout_uninit;
import static org.bytedeco.ffmpeg.global.avutil.av_frame_alloc;
import static org.bytedeco.ffmpeg.global.avutil.av_frame_free;
import static org.bytedeco.ffmpeg.global.avutil.av_frame_get_buffer;
import static org.bytedeco.ffmpeg.global.avutil.av_frame_make_writable;
import static org.bytedeco.ffmpeg.global.avutil.av_make_q;
import static org.bytedeco.ffmpeg.global.swresample.swr_alloc_set_opts2;
import static org.bytedeco.ffmpeg.global.swresample.swr_convert_frame;
import static org.bytedeco.ffmpeg.global.swresample.swr_free;
import static org.bytedeco.ffmpeg.global.swresample.swr_get_delay;
import static org.bytedeco.ffmpeg.global.swresample.swr_init;
import static org.bytedeco.ffmpeg.global.swresample.swr_is_initialized;
import static org.bytedeco.ffmpeg.global.swscale.SWS_POINT;
import static org.bytedeco.ffmpeg.global.swscale.sws_freeContext;
import static org.bytedeco.ffmpeg.global.swscale.sws_getContext;
import static org.bytedeco.ffmpeg.global.swscale.sws_isSupportedInput;
import static org.bytedeco.ffmpeg.global.swscale.sws_isSupportedOutput;
import static org.bytedeco.ffmpeg.global.swscale.sws_scale;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.bytedeco.ffmpeg.avcodec.AVCodec;
import org.bytedeco.ffmpeg.avcodec.AVCodecContext;
import org.bytedeco.ffmpeg.avcodec.AVCodecParameters;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.avformat.AVStream;
import org.bytedeco.ffmpeg.avfilter.AVFilter;
import org.bytedeco.ffmpeg.avfilter.AVFilterContext;
import org.bytedeco.ffmpeg.avfilter.AVFilterGraph;
import org.bytedeco.ffmpeg.avutil.AVChannelLayout;
import org.bytedeco.ffmpeg.avutil.AVDictionary;
import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.bytedeco.ffmpeg.swresample.SwrContext;
import org.bytedeco.ffmpeg.swscale.SwsContext;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.DoublePointer;
import org.bytedeco.javacpp.Pointer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class FfmpegTest {
    private static final int IMAGE_WIDTH = 16;
    private static final int IMAGE_HEIGHT = 12;
    private static final int SAMPLE_RATE = 8_000;

    @TempDir Path temporaryDirectory;

    @Test
    void encodesDemuxesAndDecodesPngImage() throws IOException {
        AVCodecContext encoderContext = null;
        AVCodecContext decoderContext = null;
        AVFrame sourceFrame = null;
        AVFrame decodedFrame = null;
        AVPacket encodedPacket = null;
        AVPacket demuxedPacket = null;
        AVFormatContext inputContext = new AVFormatContext((Pointer) null);

        try {
            AVCodec encoder = assertAllocated(avcodec_find_encoder(AV_CODEC_ID_PNG));
            assertThat(encoder.name().getString()).isEqualTo("png");

            encoderContext = assertAllocated(avcodec_alloc_context3(encoder));
            encoderContext
                    .width(IMAGE_WIDTH)
                    .height(IMAGE_HEIGHT)
                    .pix_fmt(AV_PIX_FMT_RGB24)
                    .time_base(av_make_q(1, 25));
            assertThat(avcodec_open2(encoderContext, encoder, (AVDictionary) null)).isZero();

            sourceFrame = assertAllocated(av_frame_alloc());
            sourceFrame
                    .width(IMAGE_WIDTH)
                    .height(IMAGE_HEIGHT)
                    .format(AV_PIX_FMT_RGB24)
                    .pts(0);
            assertThat(av_frame_get_buffer(sourceFrame, 32)).isZero();
            assertThat(av_frame_make_writable(sourceFrame)).isZero();
            fillRgbFrame(sourceFrame);

            encodedPacket = assertAllocated(av_packet_alloc());
            assertThat(avcodec_send_frame(encoderContext, sourceFrame)).isZero();
            assertThat(avcodec_receive_packet(encoderContext, encodedPacket)).isZero();
            assertThat(encodedPacket.size()).isGreaterThan(0);

            byte[] encodedImage = new byte[encodedPacket.size()];
            encodedPacket.data().get(encodedImage);
            assertPngSignature(encodedImage);

            Path imagePath = temporaryDirectory.resolve("synthetic.png");
            Files.write(imagePath, encodedImage);
            assertThat(Files.size(imagePath)).isEqualTo(encodedImage.length);

            assertThat(avformat_open_input(inputContext, imagePath.toString(), null, null)).isZero();
            assertThat(avformat_find_stream_info(inputContext, (AVDictionary) null)).isZero();
            assertThat(inputContext.nb_streams()).isEqualTo(1);

            AVStream imageStream = assertAllocated(inputContext.streams(0));
            AVCodecParameters codecParameters = assertAllocated(imageStream.codecpar());
            assertThat(codecParameters.codec_type()).isEqualTo(AVMEDIA_TYPE_VIDEO);
            assertThat(codecParameters.codec_id()).isEqualTo(AV_CODEC_ID_PNG);
            assertThat(codecParameters.width()).isEqualTo(IMAGE_WIDTH);
            assertThat(codecParameters.height()).isEqualTo(IMAGE_HEIGHT);

            demuxedPacket = assertAllocated(av_packet_alloc());
            assertThat(av_read_frame(inputContext, demuxedPacket)).isZero();
            assertThat(demuxedPacket.stream_index()).isZero();
            assertThat(demuxedPacket.size()).isGreaterThan(0);

            AVCodec decoder = assertAllocated(avcodec_find_decoder(codecParameters.codec_id()));
            decoderContext = assertAllocated(avcodec_alloc_context3(decoder));
            assertThat(avcodec_parameters_to_context(decoderContext, codecParameters)).isZero();
            assertThat(avcodec_open2(decoderContext, decoder, (AVDictionary) null)).isZero();

            decodedFrame = assertAllocated(av_frame_alloc());
            assertThat(avcodec_send_packet(decoderContext, demuxedPacket)).isZero();
            assertThat(avcodec_receive_frame(decoderContext, decodedFrame)).isZero();
            assertThat(decodedFrame.width()).isEqualTo(IMAGE_WIDTH);
            assertThat(decodedFrame.height()).isEqualTo(IMAGE_HEIGHT);
            assertThat(decodedFrame.format()).isEqualTo(AV_PIX_FMT_RGB24);
            assertFramePixels(decodedFrame);
        } finally {
            if (isAllocated(inputContext)) {
                avformat_close_input(inputContext);
            }
            if (isAllocated(demuxedPacket)) {
                av_packet_free(demuxedPacket);
            }
            if (isAllocated(decodedFrame)) {
                av_frame_free(decodedFrame);
            }
            if (isAllocated(decoderContext)) {
                avcodec_free_context(decoderContext);
            }
            if (isAllocated(encodedPacket)) {
                av_packet_free(encodedPacket);
            }
            if (isAllocated(sourceFrame)) {
                av_frame_free(sourceFrame);
            }
            if (isAllocated(encoderContext)) {
                avcodec_free_context(encoderContext);
            }
        }
    }

    @Test
    void scalesRgbImageAndConvertsItsPixelOrder() {
        byte[] rgbPixels = {
            (byte) 255, 0, 0,
            0, (byte) 255, 0,
            0, 0, (byte) 255,
            (byte) 255, (byte) 255, (byte) 255
        };
        byte[] scaledBgrPixels = new byte[4 * 4 * 3];

        assertThat(sws_isSupportedInput(AV_PIX_FMT_RGB24)).isEqualTo(1);
        assertThat(sws_isSupportedOutput(AV_PIX_FMT_BGR24)).isEqualTo(1);
        SwsContext scaleContext = assertAllocated(sws_getContext(
                2,
                2,
                AV_PIX_FMT_RGB24,
                4,
                4,
                AV_PIX_FMT_BGR24,
                SWS_POINT,
                null,
                null,
                (DoublePointer) null));

        try {
            int outputRows = sws_scale(
                    scaleContext,
                    rgbPixels,
                    new int[] {2 * 3},
                    0,
                    2,
                    scaledBgrPixels,
                    new int[] {4 * 3});
            assertThat(outputRows).isEqualTo(4);

            for (int y = 0; y < 4; y++) {
                for (int x = 0; x < 4; x++) {
                    int sourceOffset = ((y / 2) * 2 + x / 2) * 3;
                    int outputOffset = (y * 4 + x) * 3;
                    assertThat(Byte.toUnsignedInt(scaledBgrPixels[outputOffset]))
                            .isEqualTo(Byte.toUnsignedInt(rgbPixels[sourceOffset + 2]));
                    assertThat(Byte.toUnsignedInt(scaledBgrPixels[outputOffset + 1]))
                            .isEqualTo(Byte.toUnsignedInt(rgbPixels[sourceOffset + 1]));
                    assertThat(Byte.toUnsignedInt(scaledBgrPixels[outputOffset + 2]))
                            .isEqualTo(Byte.toUnsignedInt(rgbPixels[sourceOffset]));
                }
            }
        } finally {
            sws_freeContext(scaleContext);
        }
    }

    @Test
    void flipsVideoFramesThroughAFilterGraph() {
        int width = 3;
        int height = 2;
        byte[] sourcePixels = {
            (byte) 255, 0, 0,
            0, (byte) 255, 0,
            0, 0, (byte) 255,
            (byte) 255, (byte) 255, 0,
            0, (byte) 255, (byte) 255,
            (byte) 255, 0, (byte) 255
        };
        AVFilterGraph filterGraph = null;
        AVFrame inputFrame = null;
        AVFrame outputFrame = null;

        try {
            AVFilter bufferSource = assertAllocated(avfilter_get_by_name("buffer"));
            AVFilter horizontalFlip = assertAllocated(avfilter_get_by_name("hflip"));
            AVFilter bufferSink = assertAllocated(avfilter_get_by_name("buffersink"));
            filterGraph = assertAllocated(avfilter_graph_alloc());

            AVFilterContext sourceContext = new AVFilterContext((Pointer) null);
            AVFilterContext flipContext = new AVFilterContext((Pointer) null);
            AVFilterContext sinkContext = new AVFilterContext((Pointer) null);
            String sourceArguments = "video_size=" + width + "x" + height
                    + ":pix_fmt=" + AV_PIX_FMT_RGB24
                    + ":time_base=1/25:pixel_aspect=1/1";

            assertThat(avfilter_graph_create_filter(
                            sourceContext, bufferSource, "source", sourceArguments, null, filterGraph))
                    .isZero();
            assertThat(avfilter_graph_create_filter(
                            flipContext, horizontalFlip, "flip", (String) null, null, filterGraph))
                    .isZero();
            assertThat(avfilter_graph_create_filter(
                            sinkContext, bufferSink, "sink", (String) null, null, filterGraph))
                    .isZero();
            assertThat(avfilter_link(sourceContext, 0, flipContext, 0)).isZero();
            assertThat(avfilter_link(flipContext, 0, sinkContext, 0)).isZero();
            assertThat(avfilter_graph_config(filterGraph, null)).isZero();
            assertThat(filterGraph.nb_filters()).isEqualTo(3);

            inputFrame = assertAllocated(av_frame_alloc());
            inputFrame.width(width).height(height).format(AV_PIX_FMT_RGB24).pts(7);
            assertThat(av_frame_get_buffer(inputFrame, 32)).isZero();
            assertThat(av_frame_make_writable(inputFrame)).isZero();
            BytePointer inputData = inputFrame.data(0);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int sourceOffset = (y * width + x) * 3;
                    long frameOffset = (long) y * inputFrame.linesize(0) + (long) x * 3;
                    inputData.put(frameOffset, sourcePixels[sourceOffset]);
                    inputData.put(frameOffset + 1, sourcePixels[sourceOffset + 1]);
                    inputData.put(frameOffset + 2, sourcePixels[sourceOffset + 2]);
                }
            }

            assertThat(av_buffersrc_add_frame(sourceContext, inputFrame)).isZero();
            outputFrame = assertAllocated(av_frame_alloc());
            assertThat(av_buffersink_get_frame(sinkContext, outputFrame)).isZero();
            assertThat(outputFrame.width()).isEqualTo(width);
            assertThat(outputFrame.height()).isEqualTo(height);
            assertThat(outputFrame.format()).isEqualTo(AV_PIX_FMT_RGB24);
            assertThat(outputFrame.pts()).isEqualTo(7);

            BytePointer outputData = outputFrame.data(0);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int expectedOffset = (y * width + (width - x - 1)) * 3;
                    long frameOffset = (long) y * outputFrame.linesize(0) + (long) x * 3;
                    assertThat(outputData.get(frameOffset)).isEqualTo(sourcePixels[expectedOffset]);
                    assertThat(outputData.get(frameOffset + 1)).isEqualTo(sourcePixels[expectedOffset + 1]);
                    assertThat(outputData.get(frameOffset + 2)).isEqualTo(sourcePixels[expectedOffset + 2]);
                }
            }
        } finally {
            if (isAllocated(outputFrame)) {
                av_frame_free(outputFrame);
            }
            if (isAllocated(inputFrame)) {
                av_frame_free(inputFrame);
            }
            if (isAllocated(filterGraph)) {
                avfilter_graph_free(filterGraph);
            }
        }
    }

    @Test
    void convertsSignedPcmToNormalizedFloatingPoint() {
        short[] sourceSamples = {Short.MIN_VALUE, -16_384, 0, 16_384, Short.MAX_VALUE};
        float[] expectedSamples = {-1.0f, -0.5f, 0.0f, 0.5f, Short.MAX_VALUE / 32_768.0f};
        AVFrame inputFrame = null;
        AVFrame outputFrame = null;

        try (AVChannelLayout inputLayout = new AVChannelLayout();
                AVChannelLayout outputLayout = new AVChannelLayout();
                SwrContext resampleContext = new SwrContext()) {
            av_channel_layout_default(inputLayout, 1);
            av_channel_layout_default(outputLayout, 1);
            try {
                assertThat(swr_alloc_set_opts2(
                                resampleContext,
                                outputLayout,
                                AV_SAMPLE_FMT_FLT,
                                SAMPLE_RATE,
                                inputLayout,
                                AV_SAMPLE_FMT_S16,
                                SAMPLE_RATE,
                                0,
                                (Pointer) null))
                        .isZero();
                assertThat(resampleContext.isNull()).isFalse();
                assertThat(swr_init(resampleContext)).isZero();
                assertThat(swr_is_initialized(resampleContext)).isEqualTo(1);

                inputFrame = createAudioFrame(inputLayout, AV_SAMPLE_FMT_S16, sourceSamples.length);
                outputFrame = createAudioFrame(outputLayout, AV_SAMPLE_FMT_FLT, sourceSamples.length);
                BytePointer inputData = inputFrame.data(0);
                for (int i = 0; i < sourceSamples.length; i++) {
                    inputData.putShort((long) i * Short.BYTES, sourceSamples[i]);
                }

                assertThat(swr_convert_frame(resampleContext, outputFrame, inputFrame)).isZero();
                assertThat(outputFrame.nb_samples()).isEqualTo(sourceSamples.length);
                assertThat(swr_get_delay(resampleContext, SAMPLE_RATE)).isZero();

                BytePointer outputData = outputFrame.data(0);
                for (int i = 0; i < expectedSamples.length; i++) {
                    assertThat(outputData.getFloat((long) i * Float.BYTES))
                            .isCloseTo(expectedSamples[i], within(0.000_001f));
                }
            } finally {
                if (isAllocated(outputFrame)) {
                    av_frame_free(outputFrame);
                }
                if (isAllocated(inputFrame)) {
                    av_frame_free(inputFrame);
                }
                if (!resampleContext.isNull()) {
                    swr_free(resampleContext);
                }
                av_channel_layout_uninit(outputLayout);
                av_channel_layout_uninit(inputLayout);
            }
        }
    }

    private static AVFrame createAudioFrame(AVChannelLayout channelLayout, int sampleFormat, int sampleCount) {
        AVFrame frame = assertAllocated(av_frame_alloc());
        frame.format(sampleFormat).sample_rate(SAMPLE_RATE).nb_samples(sampleCount);
        assertThat(av_channel_layout_copy(frame.ch_layout(), channelLayout)).isZero();
        assertThat(av_frame_get_buffer(frame, 0)).isZero();
        return frame;
    }

    private static void fillRgbFrame(AVFrame frame) {
        BytePointer pixels = frame.data(0);
        int lineSize = frame.linesize(0);
        for (int y = 0; y < IMAGE_HEIGHT; y++) {
            for (int x = 0; x < IMAGE_WIDTH; x++) {
                long offset = (long) y * lineSize + (long) x * 3;
                pixels.putUnsigned(offset, red(x));
                pixels.putUnsigned(offset + 1, green(y));
                pixels.putUnsigned(offset + 2, blue(x, y));
            }
        }
    }

    private static void assertFramePixels(AVFrame frame) {
        BytePointer pixels = frame.data(0);
        int lineSize = frame.linesize(0);
        for (int y = 0; y < IMAGE_HEIGHT; y++) {
            for (int x = 0; x < IMAGE_WIDTH; x++) {
                long offset = (long) y * lineSize + (long) x * 3;
                assertThat(pixels.getUnsigned(offset)).isEqualTo(red(x));
                assertThat(pixels.getUnsigned(offset + 1)).isEqualTo(green(y));
                assertThat(pixels.getUnsigned(offset + 2)).isEqualTo(blue(x, y));
            }
        }
    }

    private static void assertPngSignature(byte[] encodedImage) {
        assertThat(encodedImage).hasSizeGreaterThan(8);
        assertThat(Byte.toUnsignedInt(encodedImage[0])).isEqualTo(0x89);
        assertThat(encodedImage[1]).isEqualTo((byte) 'P');
        assertThat(encodedImage[2]).isEqualTo((byte) 'N');
        assertThat(encodedImage[3]).isEqualTo((byte) 'G');
        assertThat(Byte.toUnsignedInt(encodedImage[4])).isEqualTo(0x0D);
        assertThat(Byte.toUnsignedInt(encodedImage[5])).isEqualTo(0x0A);
        assertThat(Byte.toUnsignedInt(encodedImage[6])).isEqualTo(0x1A);
        assertThat(Byte.toUnsignedInt(encodedImage[7])).isEqualTo(0x0A);
    }

    private static int red(int x) {
        return x * 11;
    }

    private static int green(int y) {
        return y * 17;
    }

    private static int blue(int x, int y) {
        return (x + y) * 7;
    }

    private static <T extends Pointer> T assertAllocated(T pointer) {
        assertThat(pointer).isNotNull();
        assertThat(pointer.isNull()).isFalse();
        return pointer;
    }

    private static boolean isAllocated(Pointer pointer) {
        return pointer != null && !pointer.isNull();
    }
}
