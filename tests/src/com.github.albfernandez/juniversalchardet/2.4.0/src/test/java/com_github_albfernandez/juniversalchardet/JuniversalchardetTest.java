/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_albfernandez.juniversalchardet;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mozilla.universalchardet.Constants;
import org.mozilla.universalchardet.EncodingDetectorInputStream;
import org.mozilla.universalchardet.EncodingDetectorOutputStream;
import org.mozilla.universalchardet.ReaderFactory;
import org.mozilla.universalchardet.UnicodeBOMInputStream;
import org.mozilla.universalchardet.UniversalDetector;

public class JuniversalchardetTest {
    private static final byte[] UTF_8_BOM = new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final String UTF_8_TEXT = "GraalVM reachability metadata \u2014 \u0395\u03bb\u03bb\u03b7\u03bd\u03b9\u03ba\u03ac \u65e5\u672c\u8a9e \u043a\u0438\u0440\u0438\u043b\u043b\u0438\u0446\u0430";

    @TempDir
    Path temporaryDirectory;

    @Test
    void detectsByteOrderMarksFromByteArrays() {
        assertThat(UniversalDetector.detectCharsetFromBOM(concat(UTF_8_BOM, "text".getBytes(StandardCharsets.UTF_8))))
                .isEqualTo(Constants.CHARSET_UTF_8);
        assertThat(UniversalDetector.detectCharsetFromBOM(new byte[] {(byte) 0xFE, (byte) 0xFF, 0, 'A'}))
                .isEqualTo(Constants.CHARSET_UTF_16BE);
        assertThat(UniversalDetector.detectCharsetFromBOM(new byte[] {(byte) 0xFF, (byte) 0xFE, 'A', 0}))
                .isEqualTo(Constants.CHARSET_UTF_16LE);
        assertThat(UniversalDetector.detectCharsetFromBOM(new byte[] {0, 0, (byte) 0xFE, (byte) 0xFF}))
                .isEqualTo(Constants.CHARSET_UTF_32BE);
        assertThat(UniversalDetector.detectCharsetFromBOM(new byte[] {(byte) 0xFF, (byte) 0xFE, 0, 0}))
                .isEqualTo(Constants.CHARSET_UTF_32LE);
        assertThat(UniversalDetector.detectCharsetFromBOM(new byte[] {(byte) 0xFE, (byte) 0xFF, 0, 0}))
                .isEqualTo(Constants.CHARSET_X_ISO_10646_UCS_4_3412);
        assertThat(UniversalDetector.detectCharsetFromBOM(new byte[] {0, 0, (byte) 0xFF, (byte) 0xFE}))
                .isEqualTo(Constants.CHARSET_X_ISO_10646_UCS_4_2143);
        assertThat(UniversalDetector.detectCharsetFromBOM(new byte[] {'p', 'l', 'a', 'i', 'n'})).isNull();
        assertThat(UniversalDetector.detectCharsetFromBOM(new byte[] {(byte) 0xFF, (byte) 0xFE})).isNull();
    }

    @Test
    void universalDetectorDetectsBomAsciiAndSupportsReset() {
        List<String> reportedCharsets = new ArrayList<>();
        UniversalDetector detector = new UniversalDetector(reportedCharsets::add);
        byte[] utf8WithBom = concat(UTF_8_BOM, UTF_8_TEXT.getBytes(StandardCharsets.UTF_8));

        detector.handleData(utf8WithBom, 0, utf8WithBom.length);
        assertThat(detector.isDone()).isTrue();
        assertThat(detector.getDetectedCharset()).isEqualTo(Constants.CHARSET_UTF_8);
        detector.dataEnd();
        assertThat(reportedCharsets).containsExactly(Constants.CHARSET_UTF_8);
        assertThat(detector.getListener()).isNotNull();

        detector.setListener(null);
        detector.reset();
        assertThat(detector.isDone()).isFalse();
        assertThat(detector.getDetectedCharset()).isNull();
        assertThat(detector.getListener()).isNull();

        byte[] ascii = "ASCII only text with tabs\tand newlines\n".getBytes(StandardCharsets.US_ASCII);
        detector.handleData(ascii, 0, 12);
        detector.handleData(ascii, 12, ascii.length - 12);
        detector.dataEnd();
        assertThat(detector.getDetectedCharset()).isEqualTo(Constants.CHARSET_US_ASCCI);
    }

    @Test
    void universalDetectorDetectsIso2022JpEscapeSequences() {
        List<String> reportedCharsets = new ArrayList<>();
        UniversalDetector detector = new UniversalDetector(reportedCharsets::add);
        byte[] iso2022Jp = new byte[] {
                0x49, 0x53, 0x4F, 0x2D, 0x32, 0x30, 0x32, 0x32, 0x2D, 0x4A, 0x50, 0x20, 0x73,
                0x61, 0x6D, 0x70, 0x6C, 0x65, 0x20, 0x1B, 0x24, 0x42, 0x46, 0x7C, 0x4B, 0x5C,
                0x38, 0x6C, 0x1B, 0x28, 0x42, 0x20, 0x74, 0x65, 0x78, 0x74
        };

        detector.handleData(iso2022Jp);

        assertThat(detector.isDone()).isTrue();
        assertThat(detector.getDetectedCharset()).isEqualTo(Constants.CHARSET_ISO_2022_JP);
        detector.dataEnd();
        assertThat(reportedCharsets).containsExactly(Constants.CHARSET_ISO_2022_JP);
    }

    @Test
    void detectCharsetReadsInputStreamPathAndFile() throws IOException {
        byte[] utf8WithBom = concat(UTF_8_BOM, UTF_8_TEXT.getBytes(StandardCharsets.UTF_8));
        Path inputFile = temporaryDirectory.resolve("utf8-with-bom.txt");
        Files.write(inputFile, utf8WithBom);

        assertThat(UniversalDetector.detectCharset(new ByteArrayInputStream(utf8WithBom)))
                .isEqualTo(Constants.CHARSET_UTF_8);
        assertThat(UniversalDetector.detectCharset(inputFile)).isEqualTo(Constants.CHARSET_UTF_8);
        assertThat(UniversalDetector.detectCharset(inputFile.toFile())).isEqualTo(Constants.CHARSET_UTF_8);

        byte[] ascii = "Plain ASCII fixture".getBytes(StandardCharsets.US_ASCII);
        assertThat(UniversalDetector.detectCharset(new ByteArrayInputStream(ascii)))
                .isEqualTo(Constants.CHARSET_US_ASCCI);
    }

    @Test
    void readerFactoryDecodesDetectedUtf8FileAndSkipsBom() throws IOException {
        Path inputFile = temporaryDirectory.resolve("reader-factory.txt");
        Files.write(inputFile, concat(UTF_8_BOM, UTF_8_TEXT.getBytes(StandardCharsets.UTF_8)));

        try (BufferedReader reader = ReaderFactory.createBufferedReader(inputFile.toFile())) {
            assertThat(reader.readLine()).isEqualTo(UTF_8_TEXT);
            assertThat(reader.read()).isEqualTo(-1);
        }

        try (Reader reader = ReaderFactory.createReaderFromFile(inputFile.toFile(), StandardCharsets.ISO_8859_1)) {
            assertThat(readAllCharacters(reader)).isEqualTo(UTF_8_TEXT);
        }
    }

    @Test
    void readerFactoryUsesProvidedDefaultCharsetWhenDetectionIsInconclusive() throws IOException {
        String text = "Fallback charset line one\nline two";
        Path inputFile = temporaryDirectory.resolve("reader-factory-fallback.txt");
        Files.write(inputFile, text.getBytes(StandardCharsets.UTF_16LE));

        assertThat(UniversalDetector.detectCharset(inputFile)).isNull();
        try (Reader reader = ReaderFactory.createReaderFromFile(inputFile.toFile(), StandardCharsets.UTF_16LE)) {
            assertThat(readAllCharacters(reader)).isEqualTo(text);
        }
    }

    @Test
    void unicodeBomInputStreamExposesAndSkipsDetectedBom() throws IOException {
        byte[] payload = concat(UTF_8_BOM, "payload".getBytes(StandardCharsets.UTF_8));

        try (UnicodeBOMInputStream stream = new UnicodeBOMInputStream(new ByteArrayInputStream(payload), false)) {
            assertThat(stream.getBOM()).isEqualTo(UnicodeBOMInputStream.BOM.UTF_8);
            assertThat(stream.getBOM().toString()).isEqualTo("UTF-8");
            assertThat(stream.getBOM().getBytes()).containsExactly(UTF_8_BOM);
            byte[] bomBytes = stream.getBOM().getBytes();
            bomBytes[0] = 0;
            assertThat(stream.getBOM().getBytes()).containsExactly(UTF_8_BOM);
            assertThat(stream.read()).isEqualTo(0xEF);
            assertThat(stream.skipBOM()).isSameAs(stream);
        }

        try (UnicodeBOMInputStream stream = new UnicodeBOMInputStream(new ByteArrayInputStream(payload))) {
            assertThat(stream.getBOM()).isEqualTo(UnicodeBOMInputStream.BOM.UTF_8);
            assertThat(readAllBytes(stream)).isEqualTo("payload".getBytes(StandardCharsets.UTF_8));
        }

        byte[] plain = "plain".getBytes(StandardCharsets.UTF_8);
        try (UnicodeBOMInputStream stream = new UnicodeBOMInputStream(new ByteArrayInputStream(plain))) {
            assertThat(stream.getBOM()).isEqualTo(UnicodeBOMInputStream.BOM.NONE);
            assertThat(stream.available()).isGreaterThanOrEqualTo(0);
            assertThat(readAllBytes(stream)).isEqualTo(plain);
        }
    }

    @Test
    void encodingDetectorInputStreamDelegatesReadsAndDetectsCharset() throws IOException {
        byte[] utf8WithBom = concat(UTF_8_BOM, UTF_8_TEXT.getBytes(StandardCharsets.UTF_8));
        ByteArrayInputStream delegate = new ByteArrayInputStream(utf8WithBom);

        try (EncodingDetectorInputStream stream = new EncodingDetectorInputStream(delegate)) {
            assertThat(stream.markSupported()).isTrue();
            stream.mark(utf8WithBom.length);
            byte[] firstChunk = new byte[7];
            assertThat(stream.read(firstChunk)).isEqualTo(firstChunk.length);
            assertThat(stream.getDetectedCharset()).isEqualTo(Constants.CHARSET_UTF_8);
            assertThat(stream.skip(3)).isEqualTo(3);

            byte[] remaining = readAllBytes(stream);
            assertThat(concat(firstChunk, new byte[] {utf8WithBom[7], utf8WithBom[8], utf8WithBom[9]}, remaining))
                    .isEqualTo(utf8WithBom);
            assertThat(stream.getDetectedCharset()).isEqualTo(Constants.CHARSET_UTF_8);
        }
    }

    @Test
    void encodingDetectorOutputStreamDelegatesWritesAndDetectsCharsetOnClose() throws IOException {
        ByteArrayOutputStream delegate = new ByteArrayOutputStream();
        byte[] asciiTail = "SCII through detector".getBytes(StandardCharsets.US_ASCII);

        EncodingDetectorOutputStream stream = new EncodingDetectorOutputStream(delegate);
        stream.write('A');
        stream.write(asciiTail, 0, 5);
        stream.write(Arrays.copyOfRange(asciiTail, 5, asciiTail.length));
        stream.flush();
        assertThat(delegate.toString(StandardCharsets.US_ASCII)).isEqualTo("ASCII through detector");
        stream.close();
        assertThat(stream.getDetectedCharset()).isEqualTo(Constants.CHARSET_US_ASCCI);

        ByteArrayOutputStream utf8Delegate = new ByteArrayOutputStream();
        EncodingDetectorOutputStream utf8Stream = new EncodingDetectorOutputStream(utf8Delegate);
        byte[] utf8WithBom = concat(UTF_8_BOM, UTF_8_TEXT.getBytes(StandardCharsets.UTF_8));
        utf8Stream.write(utf8WithBom);
        assertThat(utf8Stream.getDetectedCharset()).isEqualTo(Constants.CHARSET_UTF_8);
        utf8Stream.close();
        assertThat(utf8Delegate.toByteArray()).isEqualTo(utf8WithBom);
    }

    private static byte[] readAllBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[16];
        int count;
        while ((count = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, count);
        }
        return outputStream.toByteArray();
    }

    private static String readAllCharacters(Reader reader) throws IOException {
        StringBuilder result = new StringBuilder();
        char[] buffer = new char[16];
        int count;
        while ((count = reader.read(buffer)) != -1) {
            result.append(buffer, 0, count);
        }
        return result.toString();
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    private static byte[] concat(byte[] first, byte[] second, byte[] third) {
        return concat(concat(first, second), third);
    }
}
