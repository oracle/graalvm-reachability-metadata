/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bytedeco.libfreenect2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.libfreenect2.CpuPacketPipeline;
import org.bytedeco.libfreenect2.DataCallback;
import org.bytedeco.libfreenect2.DepthPacketProcessor;
import org.bytedeco.libfreenect2.Frame;
import org.bytedeco.libfreenect2.Freenect2;
import org.bytedeco.libfreenect2.Freenect2Device;
import org.bytedeco.libfreenect2.Freenect2Device.ColorCameraParams;
import org.bytedeco.libfreenect2.Freenect2Device.Config;
import org.bytedeco.libfreenect2.Freenect2Device.IrCameraParams;
import org.bytedeco.libfreenect2.Logger;
import org.bytedeco.libfreenect2.Registration;
import org.bytedeco.libfreenect2.RgbPacketProcessor;
import org.junit.jupiter.api.Test;

public class Libfreenect2Test {
    private static final int DEPTH_WIDTH = 512;
    private static final int DEPTH_HEIGHT = 424;
    private static final int COLOR_WIDTH = 1920;
    private static final int COLOR_HEIGHT = 1080;
    private static final int BYTES_PER_PIXEL = 4;
    private static final int DEPTH_CENTER_X = 255;
    private static final int DEPTH_CENTER_Y = 211;
    private static final int COLOR_CENTER_X = 960;
    private static final int COLOR_CENTER_Y = 540;
    private static final float DEPTH_MILLIMETERS = 2000.0f;

    @Test
    void reportsNoDevicesWhenNoKinectIsAttached() {
        try (Freenect2 freenect2 = new Freenect2()) {
            assertThat(freenect2.enumerateDevices()).isZero();

            try (BytePointer defaultSerial = freenect2.getDefaultDeviceSerialNumber();
                    BytePointer invalidSerial = freenect2.getDeviceSerialNumber(0)) {
                assertThat(pointerString(defaultSerial)).isEmpty();
                assertThat(pointerString(invalidSerial)).isEmpty();
            }

            try (Freenect2Device device = freenect2.openDefaultDevice()) {
                assertThat(device == null || device.isNull()).isTrue();
            }
        }
    }

    @Test
    void allocatesFrameAndExposesPixelStorageAndMetadata() {
        try (Frame frame = new Frame(3, 2, BYTES_PER_PIXEL)) {
            frame.format(Frame.RGBX)
                    .timestamp(1234)
                    .sequence(42)
                    .exposure(1.25f)
                    .gain(1.1f)
                    .gamma(2.2f)
                    .status(0);

            ByteBuffer pixels = frameBytes(frame);
            pixels.put(0, (byte) 10);
            pixels.put(1, (byte) 20);
            pixels.put(2, (byte) 30);
            pixels.put(3, (byte) 40);
            pixels.putInt(BYTES_PER_PIXEL, 0x10203040);

            assertThat(frame.width()).isEqualTo(3);
            assertThat(frame.height()).isEqualTo(2);
            assertThat(frame.bytes_per_pixel()).isEqualTo(BYTES_PER_PIXEL);
            assertThat(frame.format()).isEqualTo(Frame.RGBX);
            assertThat(frame.timestamp()).isEqualTo(1234);
            assertThat(frame.sequence()).isEqualTo(42);
            assertThat(frame.exposure()).isEqualTo(1.25f);
            assertThat(frame.gain()).isEqualTo(1.1f);
            assertThat(frame.gamma()).isEqualTo(2.2f);
            assertThat(frame.status()).isZero();
            assertThat(pixels.get(0)).isEqualTo((byte) 10);
            assertThat(pixels.get(1)).isEqualTo((byte) 20);
            assertThat(pixels.get(2)).isEqualTo((byte) 30);
            assertThat(pixels.get(3)).isEqualTo((byte) 40);
            assertThat(pixels.getInt(BYTES_PER_PIXEL)).isEqualTo(0x10203040);
        }
    }

    @Test
    void convertsLoggerLevelsToNames() {
        assertThat(Logger.getDefaultLevel()).isBetween(Logger.None, Logger.Debug);
        assertThat(loggerLevelName(Logger.None)).isEmpty();
        assertThat(loggerLevelName(Logger.Error)).isEqualTo("Error");
        assertThat(loggerLevelName(Logger.Warning)).isEqualTo("Warning");
        assertThat(loggerLevelName(Logger.Info)).isEqualTo("Info");
        assertThat(loggerLevelName(Logger.Debug)).isEqualTo("Debug");
    }

    @Test
    void constructsCpuPacketPipelineWithItsProcessors() {
        try (CpuPacketPipeline pipeline = new CpuPacketPipeline()) {
            assertNativePointer(pipeline);

            DataCallback rgbParser = pipeline.getRgbPacketParser();
            DataCallback irParser = pipeline.getIrPacketParser();
            RgbPacketProcessor rgbProcessor = pipeline.getRgbPacketProcessor();
            DepthPacketProcessor depthProcessor = pipeline.getDepthPacketProcessor();

            assertNativePointer(rgbParser);
            assertNativePointer(irParser);
            assertNativePointer(rgbProcessor);
            assertNativePointer(depthProcessor);
        }
    }

    @Test
    void storesDepthProcessingConfiguration() {
        try (Config config = new Config()) {
            config.MinDepth(0.5f)
                    .MaxDepth(4.5f)
                    .EnableBilateralFilter(true)
                    .EnableEdgeAwareFilter(false);

            assertThat(config.MinDepth()).isEqualTo(0.5f);
            assertThat(config.MaxDepth()).isEqualTo(4.5f);
            assertThat(config.EnableBilateralFilter()).isTrue();
            assertThat(config.EnableEdgeAwareFilter()).isFalse();
        }
    }

    @Test
    void registersSyntheticDepthAndColorFrames() {
        try (IrCameraParams irParameters = createIrParameters();
                ColorCameraParams colorParameters = createColorParameters();
                Registration registration = new Registration(irParameters, colorParameters);
                Frame depth = new Frame(DEPTH_WIDTH, DEPTH_HEIGHT, BYTES_PER_PIXEL);
                Frame color = new Frame(COLOR_WIDTH, COLOR_HEIGHT, BYTES_PER_PIXEL);
                Frame undistorted = new Frame(DEPTH_WIDTH, DEPTH_HEIGHT, BYTES_PER_PIXEL);
                Frame registered = new Frame(DEPTH_WIDTH, DEPTH_HEIGHT, BYTES_PER_PIXEL)) {
            depth.format(Frame.Float);
            color.format(Frame.BGRX);
            undistorted.format(Frame.Float);
            registered.format(Frame.BGRX);

            FloatBuffer depthPixels = frameBytes(depth).asFloatBuffer();
            while (depthPixels.hasRemaining()) {
                depthPixels.put(DEPTH_MILLIMETERS);
            }

            int colorIndex = COLOR_CENTER_Y * COLOR_WIDTH + COLOR_CENTER_X;
            ByteBuffer colorPixels = frameBytes(color);
            colorPixels.put(colorIndex * BYTES_PER_PIXEL, (byte) 10);
            colorPixels.put(colorIndex * BYTES_PER_PIXEL + 1, (byte) 20);
            colorPixels.put(colorIndex * BYTES_PER_PIXEL + 2, (byte) 30);
            colorPixels.put(colorIndex * BYTES_PER_PIXEL + 3, (byte) 40);

            registration.undistortDepth(depth, undistorted);
            int depthIndex = DEPTH_CENTER_Y * DEPTH_WIDTH + DEPTH_CENTER_X;
            FloatBuffer undistortedPixels = frameBytes(undistorted).asFloatBuffer();
            assertThat(undistortedPixels.get(depthIndex)).isEqualTo(DEPTH_MILLIMETERS);

            float[] mappedX = new float[1];
            float[] mappedY = new float[1];
            registration.apply(DEPTH_CENTER_X, DEPTH_CENTER_Y, DEPTH_MILLIMETERS, mappedX, mappedY);
            assertThat(mappedX[0]).isCloseTo(COLOR_CENTER_X, within(0.001f));
            assertThat(mappedY[0]).isCloseTo(COLOR_CENTER_Y, within(0.001f));

            int[] colorDepthMap = new int[DEPTH_WIDTH * DEPTH_HEIGHT];
            registration.apply(color, depth, undistorted, registered, false, null, colorDepthMap);
            assertThat(colorDepthMap[depthIndex]).isEqualTo(colorIndex);

            ByteBuffer registeredPixels = frameBytes(registered);
            int registeredOffset = depthIndex * BYTES_PER_PIXEL;
            assertThat(registeredPixels.get(registeredOffset)).isEqualTo((byte) 10);
            assertThat(registeredPixels.get(registeredOffset + 1)).isEqualTo((byte) 20);
            assertThat(registeredPixels.get(registeredOffset + 2)).isEqualTo((byte) 30);
            assertThat(registeredPixels.get(registeredOffset + 3)).isEqualTo((byte) 40);

            float[] x = new float[1];
            float[] y = new float[1];
            float[] z = new float[1];
            registration.getPointXYZ(undistorted, DEPTH_CENTER_Y, DEPTH_CENTER_X, x, y, z);
            assertThat(x[0]).isZero();
            assertThat(y[0]).isZero();
            assertThat(z[0]).isEqualTo(2.0f);

            float[] rgb = new float[1];
            registration.getPointXYZRGB(undistorted, registered, DEPTH_CENTER_Y, DEPTH_CENTER_X, x, y, z, rgb);
            assertThat(x[0]).isZero();
            assertThat(y[0]).isZero();
            assertThat(z[0]).isEqualTo(2.0f);
            assertThat(Float.floatToRawIntBits(rgb[0])).isEqualTo(registeredPixels.getInt(registeredOffset));
        }
    }

    private static IrCameraParams createIrParameters() {
        IrCameraParams parameters = new IrCameraParams();
        parameters.fx(365.0f)
                .fy(365.0f)
                .cx(255.5f)
                .cy(211.5f)
                .k1(0.0f)
                .k2(0.0f)
                .k3(0.0f)
                .p1(0.0f)
                .p2(0.0f);
        return parameters;
    }

    private static ColorCameraParams createColorParameters() {
        ColorCameraParams parameters = new ColorCameraParams();
        parameters.fx(1000.0f)
                .fy(1000.0f)
                .cx(COLOR_CENTER_X)
                .cy(COLOR_CENTER_Y)
                .shift_d(1.0f)
                .shift_m(0.0f)
                .mx_x3y0(0.0f)
                .mx_x0y3(0.0f)
                .mx_x2y1(0.0f)
                .mx_x1y2(0.0f)
                .mx_x2y0(0.0f)
                .mx_x0y2(0.0f)
                .mx_x1y1(0.0f)
                .mx_x1y0(0.0f)
                .mx_x0y1(0.0f)
                .mx_x0y0(0.0f)
                .my_x3y0(0.0f)
                .my_x0y3(0.0f)
                .my_x2y1(0.0f)
                .my_x1y2(0.0f)
                .my_x2y0(0.0f)
                .my_x0y2(0.0f)
                .my_x1y1(0.0f)
                .my_x1y0(0.0f)
                .my_x0y1(0.0f)
                .my_x0y0(0.0f);
        return parameters;
    }

    private static ByteBuffer frameBytes(Frame frame) {
        long byteCount = frame.width() * frame.height() * frame.bytes_per_pixel();
        assertThat(byteCount).isLessThanOrEqualTo(Integer.MAX_VALUE);
        BytePointer data = frame.data();
        assertNativePointer(data);
        return data.capacity(byteCount).asByteBuffer().order(ByteOrder.nativeOrder());
    }

    private static String loggerLevelName(int level) {
        try (BytePointer name = Logger.level2str(level)) {
            return pointerString(name);
        }
    }

    private static String pointerString(BytePointer value) {
        return value == null || value.isNull() ? "" : value.getString();
    }

    private static void assertNativePointer(Pointer pointer) {
        assertThat(pointer).isNotNull();
        assertThat(pointer.isNull()).isFalse();
    }
}
