/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bytedeco.leptonica;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.bytedeco.leptonica.global.leptonica.IFF_PNG;
import static org.bytedeco.leptonica.global.leptonica.L_COPY;
import static org.bytedeco.leptonica.global.leptonica.REMOVE_CMAP_TO_FULL_COLOR;
import static org.bytedeco.leptonica.global.leptonica.SEL_HIT;
import static org.bytedeco.leptonica.global.leptonica.boxContains;
import static org.bytedeco.leptonica.global.leptonica.boxCreate;
import static org.bytedeco.leptonica.global.leptonica.boxDestroy;
import static org.bytedeco.leptonica.global.leptonica.boxGetCenter;
import static org.bytedeco.leptonica.global.leptonica.boxGetGeometry;
import static org.bytedeco.leptonica.global.leptonica.boxOverlapRegion;
import static org.bytedeco.leptonica.global.leptonica.boxaDestroy;
import static org.bytedeco.leptonica.global.leptonica.boxaGetBox;
import static org.bytedeco.leptonica.global.leptonica.boxaGetCount;
import static org.bytedeco.leptonica.global.leptonica.kernelCreate;
import static org.bytedeco.leptonica.global.leptonica.kernelDestroy;
import static org.bytedeco.leptonica.global.leptonica.kernelGetSum;
import static org.bytedeco.leptonica.global.leptonica.kernelNormalize;
import static org.bytedeco.leptonica.global.leptonica.kernelSetElement;
import static org.bytedeco.leptonica.global.leptonica.numaAddNumber;
import static org.bytedeco.leptonica.global.leptonica.numaCreate;
import static org.bytedeco.leptonica.global.leptonica.numaDestroy;
import static org.bytedeco.leptonica.global.leptonica.numaGetCount;
import static org.bytedeco.leptonica.global.leptonica.numaGetMax;
import static org.bytedeco.leptonica.global.leptonica.numaGetMean;
import static org.bytedeco.leptonica.global.leptonica.numaGetMin;
import static org.bytedeco.leptonica.global.leptonica.numaGetSum;
import static org.bytedeco.leptonica.global.leptonica.pixClipRectangle;
import static org.bytedeco.leptonica.global.leptonica.pixConnComp;
import static org.bytedeco.leptonica.global.leptonica.pixConvertTo32;
import static org.bytedeco.leptonica.global.leptonica.pixConvolve;
import static org.bytedeco.leptonica.global.leptonica.pixCountPixels;
import static org.bytedeco.leptonica.global.leptonica.pixCreate;
import static org.bytedeco.leptonica.global.leptonica.pixDestroy;
import static org.bytedeco.leptonica.global.leptonica.pixDilate;
import static org.bytedeco.leptonica.global.leptonica.pixEqual;
import static org.bytedeco.leptonica.global.leptonica.pixErode;
import static org.bytedeco.leptonica.global.leptonica.pixGetDimensions;
import static org.bytedeco.leptonica.global.leptonica.pixGetPixel;
import static org.bytedeco.leptonica.global.leptonica.pixGetRGBPixel;
import static org.bytedeco.leptonica.global.leptonica.pixGetResolution;
import static org.bytedeco.leptonica.global.leptonica.pixInvert;
import static org.bytedeco.leptonica.global.leptonica.pixRankFilter;
import static org.bytedeco.leptonica.global.leptonica.pixRead;
import static org.bytedeco.leptonica.global.leptonica.pixRemoveColormap;
import static org.bytedeco.leptonica.global.leptonica.pixScale;
import static org.bytedeco.leptonica.global.leptonica.pixSetAllArbitrary;
import static org.bytedeco.leptonica.global.leptonica.pixSetColormap;
import static org.bytedeco.leptonica.global.leptonica.pixSetPixel;
import static org.bytedeco.leptonica.global.leptonica.pixSetResolution;
import static org.bytedeco.leptonica.global.leptonica.pixThresholdToBinary;
import static org.bytedeco.leptonica.global.leptonica.pixWrite;
import static org.bytedeco.leptonica.global.leptonica.pixcmapAddColor;
import static org.bytedeco.leptonica.global.leptonica.pixcmapCreate;
import static org.bytedeco.leptonica.global.leptonica.pixcmapDestroy;
import static org.bytedeco.leptonica.global.leptonica.pixcmapGetCount;
import static org.bytedeco.leptonica.global.leptonica.ptaAddPt;
import static org.bytedeco.leptonica.global.leptonica.ptaCreate;
import static org.bytedeco.leptonica.global.leptonica.ptaDestroy;
import static org.bytedeco.leptonica.global.leptonica.ptaGetCount;
import static org.bytedeco.leptonica.global.leptonica.ptaGetLinearLSF;
import static org.bytedeco.leptonica.global.leptonica.ptaGetRange;
import static org.bytedeco.leptonica.global.leptonica.selCreateBrick;
import static org.bytedeco.leptonica.global.leptonica.selDestroy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.bytedeco.leptonica.BOX;
import org.bytedeco.leptonica.BOXA;
import org.bytedeco.leptonica.L_KERNEL;
import org.bytedeco.leptonica.NUMA;
import org.bytedeco.leptonica.PIX;
import org.bytedeco.leptonica.PIXA;
import org.bytedeco.leptonica.PIXCMAP;
import org.bytedeco.leptonica.PTA;
import org.bytedeco.leptonica.SEL;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class LeptonicaTest {
    @TempDir Path temporaryDirectory;

    @Test
    void analyzesAndTransformsBinaryImageComponents() {
        PIX image = null;
        PIX clipped = null;
        PIX inverted = null;
        BOXA components = null;
        BOX firstComponent = null;
        BOX secondComponent = null;
        BOX clipRegion = null;
        try {
            image = requireImage(pixCreate(24, 18, 1));
            assertThat(fillRectangle(image, 2, 3, 4, 3)).isZero();
            assertThat(fillRectangle(image, 15, 10, 3, 4)).isZero();
            assertThat(pixelAt(image, 2, 3)).isOne();
            assertThat(pixelAt(image, 0, 0)).isZero();
            assertThat(countPixels(image)).isEqualTo(24);

            components = pixConnComp(image, (PIXA) null, 8);
            assertThat(components).isNotNull();
            assertThat(components.isNull()).isFalse();
            assertThat(boxaGetCount(components)).isEqualTo(2);

            firstComponent = boxaGetBox(components, 0, L_COPY);
            secondComponent = boxaGetBox(components, 1, L_COPY);
            assertThat(geometryOf(firstComponent)).containsExactly(2, 3, 4, 3);
            assertThat(geometryOf(secondComponent)).containsExactly(15, 10, 3, 4);

            clipRegion = boxCreate(1, 2, 7, 6);
            clipped = requireImage(pixClipRectangle(image, clipRegion, (BOX) null));
            assertThat(dimensionsOf(clipped)).containsExactly(7, 6, 1);
            assertThat(countPixels(clipped)).isEqualTo(12);

            inverted = requireImage(pixInvert(null, image));
            assertThat(countPixels(inverted)).isEqualTo(24 * 18 - 24);
        } finally {
            destroy(inverted);
            destroy(clipped);
            destroy(clipRegion);
            destroy(secondComponent);
            destroy(firstComponent);
            destroy(components);
            destroy(image);
        }
    }

    @Test
    void processesGrayscaleAndRgbImages() {
        PIX grayscale = null;
        PIX binary = null;
        PIX scaled = null;
        PIX ranked = null;
        PIX rgb = null;
        try {
            grayscale = requireImage(pixCreate(6, 4, 8));
            for (int y = 0; y < 4; y++) {
                for (int x = 0; x < 6; x++) {
                    assertThat(pixSetPixel(grayscale, x, y, x * 40 + y)).isZero();
                }
            }
            assertThat(pixelAt(grayscale, 5, 3)).isEqualTo(203);

            assertThat(pixSetResolution(grayscale, 300, 150)).isZero();
            int[] xResolution = new int[1];
            int[] yResolution = new int[1];
            assertThat(pixGetResolution(grayscale, xResolution, yResolution)).isZero();
            assertThat(xResolution[0]).isEqualTo(300);
            assertThat(yResolution[0]).isEqualTo(150);

            binary = requireImage(pixThresholdToBinary(grayscale, 100));
            assertThat(countPixels(binary)).isEqualTo(12);

            scaled = requireImage(pixScale(grayscale, 2.0f, 0.5f));
            assertThat(dimensionsOf(scaled)).containsExactly(12, 2, 8);

            ranked = requireImage(pixRankFilter(grayscale, 3, 3, 0.5f));
            assertThat(pixelAt(ranked, 3, 2)).isBetween(80, 163);

            rgb = requireImage(pixConvertTo32(grayscale));
            int[] red = new int[1];
            int[] green = new int[1];
            int[] blue = new int[1];
            assertThat(pixGetRGBPixel(rgb, 5, 3, red, green, blue)).isZero();
            assertThat(red[0]).isEqualTo(203);
            assertThat(green[0]).isEqualTo(203);
            assertThat(blue[0]).isEqualTo(203);
        } finally {
            destroy(rgb);
            destroy(ranked);
            destroy(scaled);
            destroy(binary);
            destroy(grayscale);
        }
    }

    @Test
    void expandsIndexedColorImageFromPalette() {
        PIX indexed = null;
        PIX expanded = null;
        PIXCMAP palette = null;
        try {
            indexed = requireImage(pixCreate(3, 2, 2));
            palette = pixcmapCreate(2);
            assertThat(palette).isNotNull();
            assertThat(palette.isNull()).isFalse();
            assertThat(pixcmapAddColor(palette, 240, 10, 20)).isZero();
            assertThat(pixcmapAddColor(palette, 30, 220, 40)).isZero();
            assertThat(pixcmapAddColor(palette, 50, 60, 230)).isZero();
            assertThat(pixcmapGetCount(palette)).isEqualTo(3);

            assertThat(pixSetColormap(indexed, palette)).isZero();
            palette = null;
            assertThat(pixSetPixel(indexed, 0, 0, 0)).isZero();
            assertThat(pixSetPixel(indexed, 1, 0, 1)).isZero();
            assertThat(pixSetPixel(indexed, 2, 0, 2)).isZero();

            expanded = requireImage(pixRemoveColormap(indexed, REMOVE_CMAP_TO_FULL_COLOR));
            assertThat(dimensionsOf(expanded)).containsExactly(3, 2, 32);
            assertThat(rgbAt(expanded, 0, 0)).containsExactly(240, 10, 20);
            assertThat(rgbAt(expanded, 1, 0)).containsExactly(30, 220, 40);
            assertThat(rgbAt(expanded, 2, 0)).containsExactly(50, 60, 230);
        } finally {
            destroy(palette);
            destroy(expanded);
            destroy(indexed);
        }
    }

    @Test
    void roundTripsPngUsingFileApi() throws IOException {
        PIX original = null;
        PIX decoded = null;
        try {
            original = requireImage(pixCreate(10, 8, 8));
            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 10; x++) {
                    assertThat(pixSetPixel(original, x, y, x * 17 + y * 3)).isZero();
                }
            }

            Path png = temporaryDirectory.resolve("gradient.png");
            assertThat(pixWrite(png.toString(), original, IFF_PNG)).isZero();
            assertThat(png).isRegularFile();
            assertThat(Files.size(png)).isPositive();

            decoded = requireImage(pixRead(png.toString()));
            assertThat(dimensionsOf(decoded)).containsExactly(10, 8, 8);
            assertThat(pixelAt(decoded, 7, 5)).isEqualTo(134);
            int[] equal = new int[1];
            assertThat(pixEqual(original, decoded, equal)).isZero();
            assertThat(equal[0]).isOne();
        } finally {
            destroy(decoded);
            destroy(original);
        }
    }

    @Test
    void computesGeometryAndNumericStatistics() {
        BOX outer = null;
        BOX inner = null;
        BOX crossing = null;
        BOX overlap = null;
        NUMA values = null;
        PTA points = null;
        try {
            outer = boxCreate(10, 20, 30, 40);
            inner = boxCreate(15, 25, 5, 7);
            crossing = boxCreate(35, 50, 20, 20);

            int[] contains = new int[1];
            assertThat(boxContains(outer, inner, contains)).isZero();
            assertThat(contains[0]).isOne();

            overlap = boxOverlapRegion(outer, crossing);
            assertThat(geometryOf(overlap)).containsExactly(35, 50, 5, 10);
            float[] centerX = new float[1];
            float[] centerY = new float[1];
            assertThat(boxGetCenter(outer, centerX, centerY)).isZero();
            assertThat(centerX[0]).isBetween(24.5f, 25.0f);
            assertThat(centerY[0]).isBetween(39.5f, 40.0f);

            values = numaCreate(0);
            assertThat(numaAddNumber(values, 3.5f)).isZero();
            assertThat(numaAddNumber(values, -2.0f)).isZero();
            assertThat(numaAddNumber(values, 7.0f)).isZero();
            assertThat(numaAddNumber(values, 3.5f)).isZero();
            assertThat(numaGetCount(values)).isEqualTo(4);

            float[] minimum = new float[1];
            float[] maximum = new float[1];
            float[] sum = new float[1];
            float[] mean = new float[1];
            int[] minimumIndex = new int[1];
            int[] maximumIndex = new int[1];
            assertThat(numaGetMin(values, minimum, minimumIndex)).isZero();
            assertThat(numaGetMax(values, maximum, maximumIndex)).isZero();
            assertThat(numaGetSum(values, sum)).isZero();
            assertThat(numaGetMean(values, mean)).isZero();
            assertThat(minimum[0]).isEqualTo(-2.0f);
            assertThat(minimumIndex[0]).isOne();
            assertThat(maximum[0]).isEqualTo(7.0f);
            assertThat(maximumIndex[0]).isEqualTo(2);
            assertThat(sum[0]).isEqualTo(12.0f);
            assertThat(mean[0]).isEqualTo(3.0f);

            points = ptaCreate(4);
            for (int x = 0; x < 4; x++) {
                assertThat(ptaAddPt(points, x, 2 * x + 1)).isZero();
            }
            assertThat(ptaGetCount(points)).isEqualTo(4);
            float[] minX = new float[1];
            float[] maxX = new float[1];
            float[] minY = new float[1];
            float[] maxY = new float[1];
            assertThat(ptaGetRange(points, minX, maxX, minY, maxY)).isZero();
            assertThat(new float[] {minX[0], maxX[0], minY[0], maxY[0]})
                    .containsExactly(0.0f, 3.0f, 1.0f, 7.0f);

            float[] slope = new float[1];
            float[] intercept = new float[1];
            assertThat(ptaGetLinearLSF(points, slope, intercept, (NUMA) null)).isZero();
            assertThat(slope[0]).isCloseTo(2.0f, within(0.0001f));
            assertThat(intercept[0]).isCloseTo(1.0f, within(0.0001f));
        } finally {
            destroy(points);
            destroy(values);
            destroy(overlap);
            destroy(crossing);
            destroy(inner);
            destroy(outer);
        }
    }

    @Test
    void appliesMorphologyAndConvolutionKernels() {
        PIX seed = null;
        PIX dilated = null;
        PIX eroded = null;
        PIX grayscale = null;
        PIX convolved = null;
        SEL structuringElement = null;
        L_KERNEL kernel = null;
        L_KERNEL normalizedKernel = null;
        try {
            seed = requireImage(pixCreate(9, 9, 1));
            assertThat(pixSetPixel(seed, 4, 4, 1)).isZero();
            structuringElement = selCreateBrick(3, 3, 1, 1, SEL_HIT);
            assertThat(structuringElement).isNotNull();
            assertThat(structuringElement.isNull()).isFalse();

            dilated = requireImage(pixDilate(null, seed, structuringElement));
            assertThat(countPixels(dilated)).isEqualTo(9);
            eroded = requireImage(pixErode(null, dilated, structuringElement));
            assertThat(countPixels(eroded)).isOne();

            kernel = kernelCreate(3, 3);
            assertThat(kernel).isNotNull();
            assertThat(kernel.isNull()).isFalse();
            for (int row = 0; row < 3; row++) {
                for (int column = 0; column < 3; column++) {
                    assertThat(kernelSetElement(kernel, row, column, 1.0f)).isZero();
                }
            }
            float[] kernelSum = new float[1];
            assertThat(kernelGetSum(kernel, kernelSum)).isZero();
            assertThat(kernelSum[0]).isEqualTo(9.0f);

            normalizedKernel = kernelNormalize(kernel, 1.0f);
            assertThat(kernelGetSum(normalizedKernel, kernelSum)).isZero();
            assertThat(kernelSum[0]).isCloseTo(1.0f, within(0.0001f));

            grayscale = requireImage(pixCreate(7, 7, 8));
            assertThat(pixSetAllArbitrary(grayscale, 100)).isZero();
            assertThat(pixSetPixel(grayscale, 3, 3, 199)).isZero();
            convolved = requireImage(pixConvolve(grayscale, normalizedKernel, 8, 1));
            assertThat(pixelAt(convolved, 3, 3)).isBetween(110, 112);
        } finally {
            destroy(convolved);
            destroy(grayscale);
            destroy(normalizedKernel);
            destroy(kernel);
            destroy(structuringElement);
            destroy(eroded);
            destroy(dilated);
            destroy(seed);
        }
    }

    private static PIX requireImage(PIX image) {
        assertThat(image).isNotNull();
        assertThat(image.isNull()).isFalse();
        return image;
    }

    private static int fillRectangle(PIX image, int x, int y, int width, int height) {
        int status = 0;
        for (int row = y; row < y + height; row++) {
            for (int column = x; column < x + width; column++) {
                status |= pixSetPixel(image, column, row, 1);
            }
        }
        return status;
    }

    private static int countPixels(PIX image) {
        int[] count = new int[1];
        assertThat(pixCountPixels(image, count, (int[]) null)).isZero();
        return count[0];
    }

    private static int pixelAt(PIX image, int x, int y) {
        int[] value = new int[1];
        assertThat(pixGetPixel(image, x, y, value)).isZero();
        return value[0];
    }

    private static int[] rgbAt(PIX image, int x, int y) {
        int[] red = new int[1];
        int[] green = new int[1];
        int[] blue = new int[1];
        assertThat(pixGetRGBPixel(image, x, y, red, green, blue)).isZero();
        return new int[] {red[0], green[0], blue[0]};
    }

    private static int[] dimensionsOf(PIX image) {
        int[] width = new int[1];
        int[] height = new int[1];
        int[] depth = new int[1];
        assertThat(pixGetDimensions(image, width, height, depth)).isZero();
        return new int[] {width[0], height[0], depth[0]};
    }

    private static int[] geometryOf(BOX box) {
        assertThat(box).isNotNull();
        assertThat(box.isNull()).isFalse();
        int[] x = new int[1];
        int[] y = new int[1];
        int[] width = new int[1];
        int[] height = new int[1];
        assertThat(boxGetGeometry(box, x, y, width, height)).isZero();
        return new int[] {x[0], y[0], width[0], height[0]};
    }

    private static void destroy(PIX image) {
        if (image != null && !image.isNull()) {
            pixDestroy(image);
        }
    }

    private static void destroy(BOX box) {
        if (box != null && !box.isNull()) {
            boxDestroy(box);
        }
    }

    private static void destroy(PIXCMAP colorMap) {
        if (colorMap != null && !colorMap.isNull()) {
            pixcmapDestroy(colorMap);
        }
    }

    private static void destroy(BOXA boxes) {
        if (boxes != null && !boxes.isNull()) {
            boxaDestroy(boxes);
        }
    }

    private static void destroy(NUMA values) {
        if (values != null && !values.isNull()) {
            numaDestroy(values);
        }
    }

    private static void destroy(PTA points) {
        if (points != null && !points.isNull()) {
            ptaDestroy(points);
        }
    }

    private static void destroy(SEL structuringElement) {
        if (structuringElement != null && !structuringElement.isNull()) {
            selDestroy(structuringElement);
        }
    }

    private static void destroy(L_KERNEL kernel) {
        if (kernel != null && !kernel.isNull()) {
            kernelDestroy(kernel);
        }
    }
}
