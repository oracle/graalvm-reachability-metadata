/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bytedeco.tesseract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.bytedeco.leptonica.global.leptonica.pixClearAll;
import static org.bytedeco.leptonica.global.leptonica.pixCreate;
import static org.bytedeco.leptonica.global.leptonica.pixDestroy;
import static org.bytedeco.leptonica.global.leptonica.pixSetPixel;
import static org.bytedeco.tesseract.global.tesseract.PSM_SINGLE_WORD;
import static org.bytedeco.tesseract.global.tesseract.RIL_WORD;
import static org.bytedeco.tesseract.global.tesseract.TessDeleteText;
import static org.bytedeco.tesseract.global.tesseract.TessResultIteratorDelete;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.leptonica.PIX;
import org.bytedeco.tesseract.ResultIterator;
import org.bytedeco.tesseract.TessBaseAPI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class TesseractTest {
    private static final String TRAINED_DATA_RESOURCE = "/tessdata/eng.traineddata";
    private static final int IMAGE_WIDTH = 300;
    private static final int IMAGE_HEIGHT = 110;
    private static final int GLYPH_SCALE = 10;
    private static final String[][] WORD_GLYPHS = {
        {"11111", "00100", "00100", "00100", "00100", "00100", "00100"},
        {"11111", "10000", "10000", "11110", "10000", "10000", "11111"},
        {"11111", "10000", "10000", "11111", "00001", "00001", "11111"},
        {"11111", "00100", "00100", "00100", "00100", "00100", "00100"}
    };

    @TempDir Path temporaryDirectory;

    @Test
    void recognizesSyntheticTextAndExposesWordResults() throws IOException {
        Path tessdataDirectory = materializeTrainedData();
        PIX image = createWordImage();

        try {
            try (TessBaseAPI api = new TessBaseAPI()) {
                try {
                    assertThat(TessBaseAPI.Version().getString()).isNotBlank();
                    assertThat(api.Init(tessdataDirectory.toAbsolutePath().toString(), "eng")).isZero();
                    assertThat(api.GetInitLanguagesAsString().getString()).contains("eng");
                    assertThat(api.SetVariable("tessedit_char_whitelist", "TEST")).isTrue();

                    api.SetPageSegMode(PSM_SINGLE_WORD);
                    assertThat(api.GetPageSegMode()).isEqualTo(PSM_SINGLE_WORD);
                    api.SetImage(image);
                    api.SetSourceResolution(300);
                    PIX inputImage = api.GetInputImage();
                    assertThat(inputImage).isNotNull();
                    assertThat(inputImage.isNull()).isFalse();
                    assertThat(inputImage.w()).isEqualTo(IMAGE_WIDTH);
                    assertThat(inputImage.h()).isEqualTo(IMAGE_HEIGHT);
                    assertThat(api.Recognize(null)).isZero();

                    String recognizedText = readAndDelete(api.GetUTF8Text());
                    assertThat(recognizedText).isNotBlank();
                    assertThat(api.MeanTextConf()).isBetween(0, 100);

                    verifyThresholdedImage(api);
                    verifyWordIterator(api, recognizedText);
                } finally {
                    api.End();
                }
            }
        } finally {
            pixDestroy(image);
        }
    }

    private Path materializeTrainedData() throws IOException {
        Path tessdataDirectory = Files.createDirectory(temporaryDirectory.resolve("tessdata"));
        Path trainedData = tessdataDirectory.resolve("eng.traineddata");
        try (InputStream resource = Objects.requireNonNull(
                TesseractTest.class.getResourceAsStream(TRAINED_DATA_RESOURCE),
                "Missing bundled English trained data")) {
            Files.copy(resource, trainedData);
        }
        assertThat(Files.size(trainedData)).isGreaterThan(0L);
        return tessdataDirectory;
    }

    private static PIX createWordImage() {
        PIX image = pixCreate(IMAGE_WIDTH, IMAGE_HEIGHT, 1);
        assertThat(image).isNotNull();
        assertThat(image.isNull()).isFalse();

        int drawingStatus = pixClearAll(image);
        int xOffset = 20;
        for (String[] glyph : WORD_GLYPHS) {
            drawingStatus |= drawGlyph(image, glyph, xOffset, 20);
            xOffset += (glyph[0].length() + 2) * GLYPH_SCALE;
        }
        assertThat(drawingStatus).isZero();
        return image;
    }

    private static int drawGlyph(PIX image, String[] glyph, int xOffset, int yOffset) {
        int status = 0;
        for (int row = 0; row < glyph.length; row++) {
            for (int column = 0; column < glyph[row].length(); column++) {
                if (glyph[row].charAt(column) == '1') {
                    status |= fillPixelBlock(image, xOffset + column * GLYPH_SCALE, yOffset + row * GLYPH_SCALE);
                }
            }
        }
        return status;
    }

    private static int fillPixelBlock(PIX image, int xOffset, int yOffset) {
        int status = 0;
        for (int y = yOffset; y < yOffset + GLYPH_SCALE; y++) {
            for (int x = xOffset; x < xOffset + GLYPH_SCALE; x++) {
                status |= pixSetPixel(image, x, y, 1);
            }
        }
        return status;
    }

    private static void verifyThresholdedImage(TessBaseAPI api) {
        PIX thresholdedImage = api.GetThresholdedImage();
        assertThat(thresholdedImage).isNotNull();
        assertThat(thresholdedImage.isNull()).isFalse();
        try {
            assertThat(thresholdedImage.w()).isEqualTo(IMAGE_WIDTH);
            assertThat(thresholdedImage.h()).isEqualTo(IMAGE_HEIGHT);
        } finally {
            pixDestroy(thresholdedImage);
        }
    }

    private static void verifyWordIterator(TessBaseAPI api, String recognizedText) {
        ResultIterator iterator = api.GetIterator();
        assertThat(iterator).isNotNull();
        assertThat(iterator.isNull()).isFalse();
        try {
            iterator.Begin();
            assertThat(iterator.IsAtBeginningOf(RIL_WORD)).isTrue();
            String firstWord = readAndDelete(iterator.GetUTF8Text(RIL_WORD)).trim();
            assertThat(firstWord).isNotBlank();
            assertThat(recognizedText).contains(firstWord);
            assertThat(iterator.Confidence(RIL_WORD)).isBetween(0.0f, 100.0f);

            int[] left = new int[1];
            int[] top = new int[1];
            int[] right = new int[1];
            int[] bottom = new int[1];
            assertThat(iterator.BoundingBox(RIL_WORD, left, top, right, bottom)).isTrue();
            assertThat(right[0]).isGreaterThan(left[0]);
            assertThat(bottom[0]).isGreaterThan(top[0]);
            if (iterator.Next(RIL_WORD)) {
                assertThat(readAndDelete(iterator.GetUTF8Text(RIL_WORD))).isNotBlank();
            }
        } finally {
            TessResultIteratorDelete(iterator);
        }
    }

    private static String readAndDelete(BytePointer text) {
        assertThat(text).isNotNull();
        assertThat(text.isNull()).isFalse();
        try {
            return text.getString();
        } finally {
            TessDeleteText(text);
        }
    }
}
