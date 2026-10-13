/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bytedeco.artoolkitplus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.bytedeco.artoolkitplus.global.ARToolKitPlus.BCH_DEFAULT_LENGTH;
import static org.bytedeco.artoolkitplus.global.ARToolKitPlus.IMAGE_FULL_RES;
import static org.bytedeco.artoolkitplus.global.ARToolKitPlus.MARKER_ID_SIMPLE;
import static org.bytedeco.artoolkitplus.global.ARToolKitPlus.PIXEL_FORMAT_LUM;
import static org.bytedeco.artoolkitplus.global.ARToolKitPlus.POSE_ESTIMATOR_ORIGINAL;
import static org.bytedeco.artoolkitplus.global.ARToolKitPlus.UNDIST_NONE;
import static org.bytedeco.artoolkitplus.global.ARToolKitPlus._free;
import static org.bytedeco.artoolkitplus.global.ARToolKitPlus.alloc;
import static org.bytedeco.artoolkitplus.global.ARToolKitPlus.allocDup;
import static org.bytedeco.artoolkitplus.global.ARToolKitPlus.createImagePattern;
import static org.bytedeco.artoolkitplus.global.ARToolKitPlus.createImagePatternBCH;
import static org.bytedeco.artoolkitplus.global.ARToolKitPlus.createImagePatternSimple;
import static org.bytedeco.artoolkitplus.global.ARToolKitPlus.dup;
import static org.bytedeco.artoolkitplus.global.ARToolKitPlus.generatePatternBCH;
import static org.bytedeco.artoolkitplus.global.ARToolKitPlus.generatePatternSimple;
import static org.bytedeco.artoolkitplus.global.ARToolKitPlus.mul;
import static org.bytedeco.artoolkitplus.global.ARToolKitPlus.selfInv;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.bytedeco.artoolkitplus.ARMat;
import org.bytedeco.artoolkitplus.ARMultiMarkerInfoT;
import org.bytedeco.artoolkitplus.BCH;
import org.bytedeco.artoolkitplus.Camera;
import org.bytedeco.artoolkitplus.MultiTracker;
import org.bytedeco.artoolkitplus.SingleTracker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class ArtoolkitplusTest {
    private static final int IMAGE_WIDTH = 320;
    private static final int IMAGE_HEIGHT = 240;
    private static final int MARKER_ID = 31;

    @TempDir
    private Path temporaryDirectory;

    @Test
    void encodesAndRepairsBchMarkerData() {
        int[] encodedBits = new int[BCH_DEFAULT_LENGTH];
        int[] errorCount = {-1};
        long[] decodedValue = {-1L};

        try (BCH encoder = new BCH()) {
            encoder.encode(encodedBits, 100L);

            assertThat(encoder.decode(errorCount, decodedValue, encodedBits)).isTrue();
            assertThat(errorCount[0]).isZero();
            assertThat(decodedValue[0]).isEqualTo(100L);

            int[] damagedBits = encodedBits.clone();
            for (int index : new int[] {0, 3, 11, 25}) {
                damagedBits[index] ^= 1;
            }

            assertThat(encoder.decode(errorCount, decodedValue, damagedBits)).isTrue();
            assertThat(errorCount[0]).isEqualTo(4);
            assertThat(decodedValue[0]).isEqualTo(100L);

            long[] encodedValue = new long[1];
            encoder.encode(encodedValue, 255L);
            assertThat(encoder.decode(errorCount, decodedValue, encodedValue[0] ^ (1L << 7)))
                    .isTrue();
            assertThat(errorCount[0]).isEqualTo(1);
            assertThat(decodedValue[0]).isEqualTo(255L);
        }
    }

    @Test
    void rendersSimpleAndBchPatternsAsBorderedBinaryImages() {
        long[] simplePattern = new long[1];
        long[] bchPattern = new long[1];
        byte[] simpleImage = new byte[64];
        byte[] imageFromPattern = new byte[64];
        byte[] bchImage = new byte[64];

        generatePatternSimple(MARKER_ID, simplePattern);
        createImagePattern(simplePattern[0], imageFromPattern);
        createImagePatternSimple(MARKER_ID, simpleImage);
        generatePatternBCH(MARKER_ID, bchPattern);
        createImagePatternBCH(MARKER_ID, bchImage);

        assertThat(simpleImage).containsExactly(imageFromPattern);
        assertThat(simpleImage).containsOnly((byte) 0, (byte) 0xff);
        assertThat(bchImage).containsOnly((byte) 0, (byte) 0xff);
        assertThat(simpleImage).isNotEqualTo(bchImage);
        assertBlackBorder(simpleImage);
        assertBlackBorder(bchImage);
        assertThat(countWhiteInteriorPixels(simpleImage)).isBetween(1, 35);
        assertThat(countWhiteInteriorPixels(bchImage)).isBetween(1, 35);
    }

    @Test
    void multipliesDuplicatesAndInvertsNativeMatrices() {
        ARMat left = alloc(2, 2);
        ARMat right = alloc(2, 2);
        ARMat product = alloc(2, 2);
        ARMat duplicate = null;

        try {
            left.m().put(new float[] {4.0f, 7.0f, 2.0f, 6.0f});
            right.m().put(new float[] {3.0f, 1.0f, 5.0f, 2.0f});

            assertThat(mul(product, left, right)).isZero();
            assertThat(readMatrix(product)).containsExactly(47.0f, 18.0f, 36.0f, 14.0f);

            duplicate = allocDup(left);
            assertThat(readMatrix(duplicate)).containsExactly(4.0f, 7.0f, 2.0f, 6.0f);
            right.m().put(new float[4]);
            assertThat(dup(right, duplicate)).isZero();
            assertThat(readMatrix(right)).containsExactly(4.0f, 7.0f, 2.0f, 6.0f);

            assertThat(selfInv(left)).isZero();
            assertThat(readMatrix(left))
                    .containsExactly(
                            new float[] {0.6f, -0.7f, -0.2f, 0.4f}, within(0.0001f));
        } finally {
            freeMatrix(duplicate);
            freeMatrix(product);
            freeMatrix(right);
            freeMatrix(left);
        }
    }

    @Test
    void loadsCalibrationAndMapsUndistortedCoordinates() throws IOException {
        Path calibrationFile = writeCalibrationFile();
        float[] idealX = new float[1];
        float[] idealY = new float[1];

        try (Camera camera = new Camera()) {
            assertThat(camera.loadFromFile(calibrationFile.toString())).isTrue();
            assertThat(camera.xsize()).isEqualTo(IMAGE_WIDTH);
            assertThat(camera.ysize()).isEqualTo(IMAGE_HEIGHT);

            camera.observ2Ideal(92.5f, 71.25f, idealX, idealY);

            assertThat(idealX[0]).isCloseTo(92.5f, within(0.001f));
            assertThat(idealY[0]).isCloseTo(71.25f, within(0.001f));
            assertThat(camera.changeFrameSize(160, 120)).isTrue();
            assertThat(camera.xsize()).isEqualTo(160);
            assertThat(camera.ysize()).isEqualTo(120);
        }
    }

    @Test
    void tracksGeneratedSimpleMarkerAndCalculatesItsPose() throws IOException {
        Path calibrationFile = writeCalibrationFile();
        byte[] image = createMarkerImage(MARKER_ID);

        try (SingleTracker tracker = new SingleTracker(IMAGE_WIDTH, IMAGE_HEIGHT)) {
            configureTracker(tracker, calibrationFile);

            int[] detectedMarkerIds = tracker.calc(image);
            assertThat(detectedMarkerIds).contains(MARKER_ID);
            assertThat(tracker.selectBestMarkerByCf()).isEqualTo(MARKER_ID);
            assertThat(tracker.getConfidence()).isGreaterThan(0.9f);

            float[] pose = new float[12];
            tracker.getARMatrix(pose);
            assertFinite(pose);
            assertThat(tracker.getModelViewMatrix()).isNotNull();
            assertThat(tracker.getProjectionMatrix()).isNotNull();
            assertThat(tracker.getNumLoadablePatterns()).isEqualTo(256);
        }
    }

    @Test
    void loadsMultiMarkerConfigurationAndDerivesMarkerGeometry() throws IOException {
        Path calibrationFile = writeCalibrationFile();
        Path markerConfiguration = writeMarkerConfiguration();

        try (MultiTracker tracker = new MultiTracker(IMAGE_WIDTH, IMAGE_HEIGHT)) {
            assertThat(tracker.setPixelFormat(PIXEL_FORMAT_LUM)).isTrue();
            assertThat(tracker.init(
                            calibrationFile.toString(), markerConfiguration.toString(), 1.0f, 1000.0f))
                    .isTrue();

            ARMultiMarkerInfoT configuration = tracker.getMultiMarkerConfig();
            assertThat(configuration).isNotNull();
            assertThat(configuration.marker_num()).isEqualTo(1);
            assertThat(configuration.prevF()).isZero();
            assertThat(configuration.marker().patt_id()).isEqualTo(MARKER_ID);
            assertThat(configuration.marker().width()).isEqualTo(40.0f);
            assertThat(configuration.marker().center(0)).isZero();
            assertThat(configuration.marker().center(1)).isZero();
            assertThat(readTransform(configuration)).containsExactly(
                    1.0f, 0.0f, 0.0f, 0.0f,
                    0.0f, 1.0f, 0.0f, 0.0f,
                    0.0f, 0.0f, 1.0f, 0.0f);
            assertThat(readInverseTransform(configuration))
                    .containsExactly(
                            new float[] {
                                1.0f, 0.0f, 0.0f, 0.0f,
                                0.0f, 1.0f, 0.0f, 0.0f,
                                0.0f, 0.0f, 1.0f, 0.0f
                            },
                            within(0.0001f));
            assertThat(readMarkerCorners(configuration)).containsExactly(
                    -20.0f, 20.0f, 0.0f,
                    20.0f, 20.0f, 0.0f,
                    20.0f, -20.0f, 0.0f,
                    -20.0f, -20.0f, 0.0f);
        }
    }

    private void configureTracker(SingleTracker tracker, Path calibrationFile) {
        assertThat(tracker.setPixelFormat(PIXEL_FORMAT_LUM)).isTrue();
        assertThat(tracker.init(calibrationFile.toString(), 1.0f, 1000.0f)).isTrue();
        assertThat(tracker.setPoseEstimator(POSE_ESTIMATOR_ORIGINAL)).isTrue();
        tracker.setMarkerMode(MARKER_ID_SIMPLE);
        tracker.setUndistortionMode(UNDIST_NONE);
        tracker.setImageProcessingMode(IMAGE_FULL_RES);
        tracker.setPatternWidth(40.0f);
        tracker.setBorderWidth(0.125f);
        tracker.setThreshold(128);
        tracker.activateAutoThreshold(false);
        assertThat(tracker.getThreshold()).isEqualTo(128);
        assertThat(tracker.isAutoThresholdActivated()).isFalse();
        assertThat(tracker.getPixelFormat()).isEqualTo(PIXEL_FORMAT_LUM);
        assertThat(tracker.getBitsPerPixel()).isEqualTo(8);
    }

    private Path writeCalibrationFile() throws IOException {
        Path calibrationFile = temporaryDirectory.resolve("camera.cal");
        Files.writeString(
                calibrationFile,
                """
                ARToolKitPlus_CamCal_Rev02
                320 240 160 120 300.0 300.0 0.0 0.0 0.0 0.0 0.0 0.0 0
                """);
        return calibrationFile;
    }

    private Path writeMarkerConfiguration() throws IOException {
        Path configurationFile = temporaryDirectory.resolve("single-marker.cfg");
        Files.writeString(
                configurationFile,
                """
                1
                31
                40.0
                0.0 0.0
                1.0 0.0 0.0 0.0
                0.0 1.0 0.0 0.0
                0.0 0.0 1.0 0.0
                """);
        return configurationFile;
    }

    private static byte[] createMarkerImage(int markerId) {
        byte[] marker = new byte[64];
        byte[] image = new byte[IMAGE_WIDTH * IMAGE_HEIGHT];
        int cellSize = 20;
        int markerWidth = 8 * cellSize;
        int startX = (IMAGE_WIDTH - markerWidth) / 2;
        int startY = (IMAGE_HEIGHT - markerWidth) / 2;
        Arrays.fill(image, (byte) 0xff);
        createImagePatternSimple(markerId, marker);

        for (int markerY = 0; markerY < 8; markerY++) {
            for (int markerX = 0; markerX < 8; markerX++) {
                byte value = marker[markerY * 8 + markerX];
                for (int y = 0; y < cellSize; y++) {
                    int rowOffset = (startY + markerY * cellSize + y) * IMAGE_WIDTH;
                    Arrays.fill(
                            image,
                            rowOffset + startX + markerX * cellSize,
                            rowOffset + startX + (markerX + 1) * cellSize,
                            value);
                }
            }
        }
        return image;
    }

    private static float[] readMatrix(ARMat matrix) {
        float[] values = new float[matrix.row() * matrix.clm()];
        matrix.m().get(values);
        return values;
    }

    private static float[] readTransform(ARMultiMarkerInfoT configuration) {
        float[] transform = new float[12];
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 4; column++) {
                transform[row * 4 + column] = configuration.marker().trans(row, column);
            }
        }
        return transform;
    }

    private static float[] readInverseTransform(ARMultiMarkerInfoT configuration) {
        float[] transform = new float[12];
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 4; column++) {
                transform[row * 4 + column] = configuration.marker().itrans(row, column);
            }
        }
        return transform;
    }

    private static float[] readMarkerCorners(ARMultiMarkerInfoT configuration) {
        float[] corners = new float[12];
        for (int corner = 0; corner < 4; corner++) {
            for (int coordinate = 0; coordinate < 3; coordinate++) {
                corners[corner * 3 + coordinate] = configuration.marker().pos3d(corner, coordinate);
            }
        }
        return corners;
    }

    private static void freeMatrix(ARMat matrix) {
        if (matrix != null && !matrix.isNull()) {
            _free(matrix);
        }
    }

    private static void assertBlackBorder(byte[] image) {
        for (int index = 0; index < 8; index++) {
            assertThat(image[index]).isZero();
            assertThat(image[56 + index]).isZero();
            assertThat(image[index * 8]).isZero();
            assertThat(image[index * 8 + 7]).isZero();
        }
    }

    private static int countWhiteInteriorPixels(byte[] image) {
        int whitePixels = 0;
        for (int y = 1; y < 7; y++) {
            for (int x = 1; x < 7; x++) {
                if (image[y * 8 + x] == (byte) 0xff) {
                    whitePixels++;
                }
            }
        }
        return whitePixels;
    }

    private static void assertFinite(float[] values) {
        for (float value : values) {
            assertThat(Float.isFinite(value)).isTrue();
        }
    }
}
