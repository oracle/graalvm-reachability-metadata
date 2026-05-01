/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_brotli.dec;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

import org.brotli.dec.BrotliInputStream;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class DecTest {

    private static final byte GUARD_BYTE = (byte) 0x5A;

    private static final String TEXT = (
            "Brotli streams can be decoded a few bytes at a time. \u041f\u0440\u0438\u0432\u0435\u0442, \u4e16\u754c!\n").repeat(4);

    private static final String CUSTOM_DICTIONARY_TEXT =
            "common-prefix-https://www.example.com/assets/ shared dictionary seed";

    private static final String CUSTOM_DICTIONARY_PAYLOAD =
            "common-prefix-common-prefix-tail https://www.example.com/assets/app.css";

    private static final byte[] EMPTY_COMPRESSED = decodeBase64("Bg==");

    private static final byte[] TEXT_COMPRESSED = decodeBase64("""
            YiUAgJEaqWluB+W2bklnF0swNWULgiC4L4bWBWEDDtjjtIl3ygF70LxFmCTWbqj3HJbnQD6X1bKd
            rTUJcQUzXJkz1njXv8aVB12AxpVO5Un/b0PhfT9zsgM=
            """);

    private static final byte[] REPETITIVE_COMPRESSED = decodeBase64("""
            IpgAgMDzbS7VFxLXiYRMKDCTL5vr5MDhu/TtIsc0gcAxUQnhZElIgmmXzCSPHT1vqS7PTi8v87Xp
            gdyd/8D4yF/L/9HKECiYyeVcJ5xbICq06kgjm26ZZGdAYhEPM8HcPETuArwQjlEB
            """);

    private static final byte[] BINARY_COMPRESSED = decodeBase64("""
            4n8AgJEaqWluNzBOn9orQx5AlU6c+y4ObJtZPdYVG7HyPcCozAkim66677STUwDf1x19PyVhhr3W
            fTlxPURDoXa/pp2jVMnidoLXQcSh9Ozvo4hz3K3B+00YoFrKzeOe5cRpM9DNgmyCy2K5FYpRAwT+
            3vj7KYkwHfXuy4nnJRkLtfsN3YZWJYu7KUEPEYeA+f9XEed42sOPmzBAs1Zaxz3LhddloZsFxYyQ
            x3KrEVYDFPh7k5+XNMJ0NHqvZ56XZCrWHzZ0G0adKu2mBANUEgbm/z9lgutpDz/voiDNWmk/Hdgu
            vC5XuV1SzAhFPL8eYTUgYaA/+XnJoixno/d64fvIpmL9sGXYMeoUuJ8RDVBJJLOAf8oEz9sZft5F
            Ibqt2n46cNwEfa5yu6KeIRXx/GaM04KEgcH09y2LslzN/tuF76OYS43DluHAatLgfkYywqSRzAL5
            q5I8b2f09RCH6LZa5/nIcRMM+erdinqGUiYKmzFOBxYFB9PfjzzGdjX771eBn2IuNR93zCusJlO+
            npOMMFk0u0T+qhTf1x19PSRhhr3WeeaRr1ImgEXkMVeBx53ytWgW3yfMwPVY02wn0jPuFqqFE4ds
            gmKMv9W7jAVoFT3EV+HjpnWUhchjgALSiDPPw0ZpJwzgegRp2C5LivVIf8LZQDaxYxAN4J/hp2qb
            PgepoAV5y7xdNA7SIGlEkkcccuS4WylsBAfYLn4K5pU5Kb1EDgI=
            """);

    private static final byte[] CUSTOM_DICTIONARY_COMPRESSED = decodeBase64("""
            E0YA4IzUSK09qwYJg6NTaWwpjCbt1McEANxZMbNCAA==
            """);

    @Test
    void decodesUtf8TextWithOffsetReads() throws IOException {
        byte[] expectedBytes = TEXT.getBytes(StandardCharsets.UTF_8);

        try (BrotliInputStream inputStream = newBrotliStream(TEXT_COMPRESSED)) {
            byte[] decoded = readWithOffset(inputStream, 11);

            assertThat(decoded).isEqualTo(expectedBytes);
            assertThat(new String(decoded, StandardCharsets.UTF_8)).isEqualTo(TEXT);
        }
    }

    @Test
    void decodesRepeatedTextWithSingleByteReadsAndSmallInternalBuffer() throws IOException {
        String expectedText = "The quick brown fox jumps over the lazy dog. ".repeat(25)
                + "Brotli static dictionary words: compression transformation internationalization localization.";

        try (BrotliInputStream inputStream = new BrotliInputStream(
                new ByteArrayInputStream(REPETITIVE_COMPRESSED), 2)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            int value;
            while ((value = inputStream.read()) != -1) {
                assertThat(value).isBetween(0, 255);
                output.write(value);
            }

            assertThat(output.toString(StandardCharsets.UTF_8)).isEqualTo(expectedText);
            assertThat(inputStream.read()).isEqualTo(-1);
        }
    }

    @Test
    void supportsInterleavingSingleByteAndBulkReads() throws IOException {
        byte[] expectedBytes = TEXT.getBytes(StandardCharsets.UTF_8);

        try (BrotliInputStream inputStream = new BrotliInputStream(
                new ByteArrayInputStream(TEXT_COMPRESSED), 16)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            for (int index = 0; index < 3; index++) {
                int value = inputStream.read();
                assertThat(value).isBetween(0, 255);
                output.write(value);
            }

            byte[] scratch = new byte[19];
            Arrays.fill(scratch, GUARD_BYTE);
            int read;
            while ((read = inputStream.read(scratch, 4, 11)) != -1) {
                assertThat(read).isBetween(1, 11);
                assertThat(scratch[0]).isEqualTo(GUARD_BYTE);
                assertThat(scratch[3]).isEqualTo(GUARD_BYTE);
                assertThat(scratch[15]).isEqualTo(GUARD_BYTE);
                assertThat(scratch[18]).isEqualTo(GUARD_BYTE);
                output.write(scratch, 4, read);
            }

            assertThat(output.toByteArray()).isEqualTo(expectedBytes);
            assertThat(inputStream.read()).isEqualTo(-1);
        }
    }

    @Test
    void decodesBinaryDataAcrossManyPartialBufferReads() throws IOException {
        byte[] expectedBytes = expectedBinaryPayload();

        try (BrotliInputStream inputStream = new BrotliInputStream(
                new ByteArrayInputStream(BINARY_COMPRESSED), BrotliInputStream.DEFAULT_INTERNAL_BUFFER_SIZE)) {
            byte[] decoded = readWithOffset(inputStream, 7);

            assertThat(decoded).isEqualTo(expectedBytes);
        }
    }

    @Test
    void handlesEmptyStreamsAndZeroLengthReads() throws IOException {
        byte[] destination = new byte[] {1, 2, 3, 4};

        try (BrotliInputStream inputStream = newBrotliStream(EMPTY_COMPRESSED)) {
            assertThat(inputStream.read(destination, 1, 0)).isZero();
            assertThat(destination).containsExactly(1, 2, 3, 4);
            assertThat(inputStream.read()).isEqualTo(-1);
            assertThat(inputStream.read(destination, 0, destination.length)).isEqualTo(-1);
        }
    }

    @Test
    void acceptsEmptyCustomDictionaryConstructorArgument() throws IOException {
        byte[] customDictionary = new byte[0];

        try (BrotliInputStream inputStream = new BrotliInputStream(
                new ByteArrayInputStream(TEXT_COMPRESSED), 4, customDictionary)) {
            byte[] decoded = readStreamFully(inputStream);

            assertThat(new String(decoded, StandardCharsets.UTF_8)).isEqualTo(TEXT);
        }
    }

    @Test
    void decodesStreamWithNonEmptyCustomDictionary() throws IOException {
        byte[] customDictionary = CUSTOM_DICTIONARY_TEXT.getBytes(StandardCharsets.UTF_8);

        try (BrotliInputStream inputStream = new BrotliInputStream(
                new ByteArrayInputStream(CUSTOM_DICTIONARY_COMPRESSED), 4, customDictionary)) {
            byte[] decoded = readStreamFully(inputStream);

            assertThat(new String(decoded, StandardCharsets.UTF_8)).isEqualTo(CUSTOM_DICTIONARY_PAYLOAD);
        }
    }

    @Test
    void rejectsInvalidConstructorAndReadArguments() throws IOException {
        assertThatThrownBy(() -> new BrotliInputStream(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BrotliInputStream(new ByteArrayInputStream(EMPTY_COMPRESSED), 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BrotliInputStream(new ByteArrayInputStream(EMPTY_COMPRESSED), -1))
                .isInstanceOf(IllegalArgumentException.class);

        try (BrotliInputStream inputStream = newBrotliStream(EMPTY_COMPRESSED)) {
            byte[] destination = new byte[8];

            assertThatThrownBy(() -> inputStream.read(destination, -1, 1))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> inputStream.read(destination, 0, -1))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> inputStream.read(destination, 5, 4))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void reportsCorruptStreamsAsIoExceptions() {
        byte[] corruptedStream = new byte[] {(byte) 0xFF, 0x00, 0x10, 0x42};

        assertThatThrownBy(() -> readStreamFully(new BrotliInputStream(new ByteArrayInputStream(corruptedStream), 8)))
                .isInstanceOf(IOException.class);
    }

    @Test
    void closeClosesUnderlyingInputStream() throws IOException {
        TrackingInputStream source = new TrackingInputStream(TEXT_COMPRESSED);
        BrotliInputStream inputStream = new BrotliInputStream(source, 8);

        assertThat(source.closed).isFalse();
        inputStream.close();

        assertThat(source.closed).isTrue();
    }

    private static BrotliInputStream newBrotliStream(byte[] compressedBytes) throws IOException {
        return new BrotliInputStream(new ByteArrayInputStream(compressedBytes));
    }

    private static byte[] readWithOffset(InputStream inputStream, int chunkSize) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] scratch = new byte[chunkSize + 4];
        Arrays.fill(scratch, GUARD_BYTE);
        int read;
        while ((read = inputStream.read(scratch, 2, chunkSize)) != -1) {
            assertThat(read).isBetween(1, chunkSize);
            assertGuardBytes(scratch, chunkSize);
            output.write(scratch, 2, read);
        }
        assertGuardBytes(scratch, chunkSize);
        return output.toByteArray();
    }

    private static void assertGuardBytes(byte[] scratch, int chunkSize) {
        assertThat(scratch[0]).isEqualTo(GUARD_BYTE);
        assertThat(scratch[1]).isEqualTo(GUARD_BYTE);
        assertThat(scratch[chunkSize + 2]).isEqualTo(GUARD_BYTE);
        assertThat(scratch[chunkSize + 3]).isEqualTo(GUARD_BYTE);
    }

    private static byte[] readStreamFully(InputStream inputStream) throws IOException {
        try (InputStream source = inputStream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[23];
            int read;
            while ((read = source.read(buffer, 0, buffer.length)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private static byte[] expectedBinaryPayload() {
        byte[] expectedBytes = new byte[1024];
        for (int index = 0; index < expectedBytes.length; index++) {
            expectedBytes[index] = (byte) ((index * 31 + index / 7) & 0xFF);
        }
        return expectedBytes;
    }

    private static byte[] decodeBase64(String value) {
        return Base64.getMimeDecoder().decode(value);
    }

    private static final class TrackingInputStream extends ByteArrayInputStream {

        private boolean closed;

        private TrackingInputStream(byte[] bytes) {
            super(bytes);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }
}
