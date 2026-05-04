/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_googlecode_juniversalchardet.juniversalchardet;

import org.junit.jupiter.api.Test;
import org.mozilla.universalchardet.CharsetListener;
import org.mozilla.universalchardet.Constants;
import org.mozilla.universalchardet.UniversalDetector;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class JuniversalchardetTest {
    @Test
    void detectsUnicodeByteOrderMarksAndReportsListener() {
        byte[][] samples = {
                bytes(0xEF, 0xBB, 0xBF, 'u', 't', 'f', '8'),
                bytes(0xFE, 0xFF, 0x00, 'A'),
                bytes(0xFF, 0xFE, 'A', 0x00),
                bytes(0x00, 0x00, 0xFE, 0xFF),
                bytes(0xFF, 0xFE, 0x00, 0x00),
                bytes(0xFE, 0xFF, 0x00, 0x00),
                bytes(0x00, 0x00, 0xFF, 0xFE),
        };
        String[] expectedCharsets = {
                Constants.CHARSET_UTF_8,
                Constants.CHARSET_UTF_16BE,
                Constants.CHARSET_UTF_16LE,
                Constants.CHARSET_UTF_32BE,
                Constants.CHARSET_UTF_32LE,
                Constants.CHARSET_X_ISO_10646_UCS_4_3412,
                Constants.CHARSET_X_ISO_10646_UCS_4_2143,
        };

        for (int index = 0; index < samples.length; index++) {
            List<String> reports = new ArrayList<>();
            UniversalDetector detector = new UniversalDetector(reports::add);

            detector.handleData(samples[index], 0, samples[index].length);

            assertThat(detector.isDone()).isTrue();
            assertThat(detector.getDetectedCharset()).isEqualTo(expectedCharsets[index]);

            detector.handleData(bytes(0x00, 0x00, 0xFE, 0xFF), 0, 4);
            detector.dataEnd();

            assertThat(detector.getDetectedCharset()).isEqualTo(expectedCharsets[index]);
            assertThat(reports).containsExactly(expectedCharsets[index]);
        }
    }

    @Test
    void detectsUtf8TextFromMultipleOffsetChunksAndCanBeReset() {
        List<String> initialReports = new ArrayList<>();
        List<String> replacementReports = new ArrayList<>();
        CharsetListener initialListener = initialReports::add;
        CharsetListener replacementListener = replacementReports::add;
        UniversalDetector detector = new UniversalDetector(initialListener);
        byte[] utf8 = (
                "GraalVM native images exercise the same detector API. "
                        + "Zażółć gęślą jaźń. Привет мир. こんにちは世界。 "
                        + "Zażółć gęślą jaźń. Привет мир. こんにちは世界。")
                .getBytes(StandardCharsets.UTF_8);

        assertThat(detector.getListener()).isSameAs(initialListener);
        detector.setListener(replacementListener);
        assertThat(detector.getListener()).isSameAs(replacementListener);

        feedWithOffsetChunks(detector, utf8, 9);
        detector.dataEnd();

        assertThat(detector.getDetectedCharset()).isEqualTo(Constants.CHARSET_UTF_8);
        assertThat(initialReports).isEmpty();
        assertThat(replacementReports).containsExactly(Constants.CHARSET_UTF_8);

        detector.reset();
        assertThat(detector.isDone()).isFalse();
        assertThat(detector.getDetectedCharset()).isNull();

        byte[] ascii = "Plain ASCII by itself is intentionally not reported as a charset."
                .getBytes(StandardCharsets.US_ASCII);
        feedWithOffsetChunks(detector, ascii, 11);
        detector.dataEnd();

        assertThat(detector.isDone()).isFalse();
        assertThat(detector.getDetectedCharset()).isNull();
        assertThat(replacementReports).containsExactly(Constants.CHARSET_UTF_8);
    }

    @Test
    void detectsEscapedIso2022JapaneseSequence() {
        List<String> reports = new ArrayList<>();
        UniversalDetector detector = new UniversalDetector(reports::add);
        byte[] iso2022Japanese = bytes(
                'H', 'e', 'l', 'l', 'o', ' ',
                0x1B, '$', 'B', 0x46, 0x7C, 0x4B, 0x5C, 0x38, 0x6C, 0x1B, '(', 'B',
                ' ', 'f', 'r', 'o', 'm', ' ',
                0x1B, '$', 'B', 0x46, 0x7C, 0x4B, 0x5C, 0x38, 0x6C, 0x1B, '(', 'B');

        feedWithOffsetChunks(detector, iso2022Japanese, 5);
        detector.dataEnd();

        assertThat(detector.isDone()).isTrue();
        assertThat(detector.getDetectedCharset()).isEqualTo(Constants.CHARSET_ISO_2022_JP);
        assertThat(reports).containsExactly(Constants.CHARSET_ISO_2022_JP);
    }

    @Test
    void handlesEmptyAndZeroLengthInputWithoutReportingCharset() {
        List<String> reports = new ArrayList<>();
        UniversalDetector detector = new UniversalDetector(reports::add);
        byte[] data = "ignored".getBytes(StandardCharsets.US_ASCII);

        detector.handleData(data, 0, 0);
        detector.dataEnd();

        assertThat(detector.isDone()).isFalse();
        assertThat(detector.getDetectedCharset()).isNull();
        assertThat(reports).isEmpty();
    }

    private static void feedWithOffsetChunks(UniversalDetector detector, byte[] data, int chunkSize) {
        int sourceOffset = 0;
        while (sourceOffset < data.length && !detector.isDone()) {
            int count = Math.min(chunkSize, data.length - sourceOffset);
            byte[] paddedChunk = new byte[count + 5];
            System.arraycopy(data, sourceOffset, paddedChunk, 2, count);
            detector.handleData(paddedChunk, 2, count);
            sourceOffset += count;
        }
    }

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int index = 0; index < values.length; index++) {
            result[index] = (byte) values[index];
        }
        return result;
    }
}
