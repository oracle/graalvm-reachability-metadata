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
                        + "Za\u017c\u00f3\u0142\u0107 g\u0119\u015bl\u0105 ja\u017a\u0144. "
                        + "\u041f\u0440\u0438\u0432\u0435\u0442 \u043c\u0438\u0440. "
                        + "\u3053\u3093\u306b\u3061\u306f\u4e16\u754c\u3002 "
                        + "Za\u017c\u00f3\u0142\u0107 g\u0119\u015bl\u0105 ja\u017a\u0144. "
                        + "\u041f\u0440\u0438\u0432\u0435\u0442 \u043c\u0438\u0440. "
                        + "\u3053\u3093\u306b\u3061\u306f\u4e16\u754c\u3002")
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
    void detectsSingleByteCyrillicTextAfterDataEnd() {
        UniversalDetector detector = new UniversalDetector(null);
        byte[] windows1251RussianText = repeat(bytes(
                0xCF, 0xF0, 0xE8, 0xE2, 0xE5, 0xF2, ' ', 0xEC, 0xE8, 0xF0, '.', ' ',
                0xDD, 0xF2, 0xEE, ' ', 0xF0, 0xF3, 0xF1, 0xF1, 0xEA, 0xE8, 0xE9, ' ',
                0xF2, 0xE5, 0xEA, 0xF1, 0xF2, '.', ' ',
                0xC4, 0xEB, 0xFF, ' ', 0xEF, 0xF0, 0xEE, 0xE2, 0xE5, 0xF0, 0xEA, 0xE8, ' ',
                0xEE, 0xEF, 0xF0, 0xE5, 0xE4, 0xE5, 0xEB, 0xE5, 0xED, 0xE8, 0xFF, ' ',
                0xEA, 0xEE, 0xE4, 0xE8, 0xF0, 0xEE, 0xE2, 0xEA, 0xE8, '.', ' '), 12);

        feedWithOffsetChunks(detector, windows1251RussianText, 17);
        detector.dataEnd();

        assertThat(detector.getDetectedCharset()).isEqualTo(Constants.CHARSET_WINDOWS_1251);
    }

    @Test
    void detectsMultiByteEastAsianTextAfterDataEnd() {
        String[] expectedCharsets = {
                Constants.CHARSET_BIG5,
                Constants.CHARSET_EUC_KR,
                Constants.CHARSET_SHIFT_JIS,
                Constants.CHARSET_GB18030,
        };
        byte[][] samples = {
                repeat(bytes(
                        0xB3, 'o', 0xAC, 'O', 0xA4, 0x40, 0xAC, 'q', 0xC1, 'c', 0xC5, 0xE9,
                        0xA4, 0xA4, 0xA4, 0xE5, 0xA4, 0xE5, 0xA6, 'r', 0xA1, 'A', 0xA5, 0xCE,
                        0xA8, 0xD3, 0xB4, 0xFA, 0xB8, 0xD5, 'B', 'i', 'g', '5', 0xBD, 's',
                        0xBD, 'X', 0xB0, 0xBB, 0xB4, 0xFA, 0xA1, 'C'), 6),
                repeat(bytes(
                        0xBE, 0xC8, 0xB3, 0xE7, 0xC7, 0xCF, 0xBC, 0xBC, 0xBF, 0xE4, ' ', 0xBC,
                        0xBC, 0xB0, 0xE8, 0xC0, 0xD4, 0xB4, 0xCF, 0xB4, 0xD9, '.', ' ', 0xC7,
                        0xD1, 0xB1, 0xB9, 0xBE, 0xEE, ' ', 0xB9, 0xAE, 0xC0, 0xDA, 0xB8, 0xA6,
                        ' ', 0xB0, 0xA8, 0xC1, 0xF6, 0xC7, 0xCF, 0xB1, 0xE2, ' ', 0xC0, 0xA7,
                        0xC7, 0xD1, ' ', 0xC5, 0xD7, 0xBD, 0xBA, 0xC6, 0xAE, 0xC0, 0xD4, 0xB4,
                        0xCF, 0xB4, 0xD9, '.'), 6),
                repeat(bytes(
                        0x82, 0xB1, 0x82, 0xF1, 0x82, 0xC9, 0x82, 0xBF, 0x82, 0xCD, 0x90, 0xA2,
                        0x8A, 'E', 0x81, 'B', 0x93, 0xFA, 0x96, 0x7B, 0x8C, 0xEA, 0x82, 0xCC,
                        0x95, 0xB6, 0x8E, 0x9A, 0x83, 'R', 0x81, 0x5B, 0x83, 'h', 0x82, 0xF0,
                        0x94, 0xBB, 0x92, 0xE8, 0x82, 0xB7, 0x82, 0xE9, 0x82, 0xBD, 0x82, 0xDF,
                        0x82, 0xCC, 0x83, 'e', 0x83, 'X', 0x83, 'g', 0x82, 0xC5, 0x82, 0xB7,
                        0x81, 'B'), 6),
                repeat(bytes(
                        0xD5, 0xE2, 0xCA, 0xC7, 0xD2, 0xBB, 0xB8, 0xF6, 0xBC, 0xF2, 0xCC, 0xE5,
                        0xD6, 0xD0, 0xCE, 0xC4, 0xCE, 0xC4, 0xB1, 0xBE, 0xA3, 0xAC, 0xD3, 0xC3,
                        0xD3, 0xDA, 0xB2, 0xE2, 0xCA, 0xD4, 'G', 'B', '1', '8', '0', '3',
                        '0', 0xB1, 0xE0, 0xC2, 0xEB, 0xBC, 0xEC, 0xB2, 0xE2, 0xA1, 0xA3), 6),
        };

        for (int index = 0; index < samples.length; index++) {
            List<String> reports = new ArrayList<>();
            UniversalDetector detector = new UniversalDetector(reports::add);

            feedWithOffsetChunks(detector, samples[index], 13);
            detector.dataEnd();

            assertThat(detector.getDetectedCharset()).isEqualTo(expectedCharsets[index]);
            assertThat(reports).containsExactly(expectedCharsets[index]);
        }
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

    private static byte[] repeat(byte[] value, int count) {
        byte[] result = new byte[value.length * count];
        for (int index = 0; index < count; index++) {
            System.arraycopy(value, 0, result, index * value.length, value.length);
        }
        return result;
    }

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int index = 0; index < values.length; index++) {
            result[index] = (byte) values[index];
        }
        return result;
    }
}
