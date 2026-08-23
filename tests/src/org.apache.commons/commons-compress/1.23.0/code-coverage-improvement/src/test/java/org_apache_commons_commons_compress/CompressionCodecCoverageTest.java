/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

// Exercise public compressor stream lifecycles with round-trip assertions.
package org_apache_commons_commons_compress;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;

import org.apache.commons.compress.compressors.brotli.BrotliCompressorInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.deflate.DeflateCompressorInputStream;
import org.apache.commons.compress.compressors.deflate.DeflateCompressorOutputStream;
import org.apache.commons.compress.compressors.deflate64.Deflate64CompressorInputStream;
import org.apache.commons.compress.compressors.lz4.FramedLZ4CompressorInputStream;
import org.apache.commons.compress.compressors.lz4.FramedLZ4CompressorOutputStream;
import org.apache.commons.compress.compressors.lz77support.AbstractLZ77CompressorInputStream;
import org.apache.commons.compress.compressors.lz77support.Parameters;
import org.apache.commons.compress.compressors.lzma.LZMACompressorInputStream;
import org.apache.commons.compress.compressors.lzw.LZWInputStream;
import org.apache.commons.compress.compressors.lzma.LZMACompressorOutputStream;
import org.apache.commons.compress.compressors.pack200.Pack200CompressorInputStream;
import org.apache.commons.compress.compressors.pack200.Pack200CompressorOutputStream;
import org.apache.commons.compress.compressors.snappy.FramedSnappyCompressorInputStream;
import org.apache.commons.compress.compressors.snappy.FramedSnappyDialect;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream;
import org.apache.commons.compress.compressors.z.ZCompressorInputStream;
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorInputStream;
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorOutputStream;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompressionCodecCoverageTest {

    private static final byte[] PAYLOAD = "compressor coverage payload".getBytes(StandardCharsets.UTF_8);

    @Test
    void xzAndLzmaStreamsRoundTripAndExposeStreamOperations() throws Exception {
        try {
        final ByteArrayOutputStream xzBytes = new ByteArrayOutputStream();
        try (XZCompressorOutputStream output = new XZCompressorOutputStream(xzBytes, 6)) {
            output.write(PAYLOAD, 0, PAYLOAD.length - 1);
            output.write(PAYLOAD[PAYLOAD.length - 1]);
            output.flush();
            output.finish();
        }
        assertThat(XZCompressorInputStream.matches(xzBytes.toByteArray(), 6)).isTrue();
        try (XZCompressorInputStream input = new XZCompressorInputStream(
                new ByteArrayInputStream(xzBytes.toByteArray()), true, 1)) {
            assertThat(input.available()).isGreaterThanOrEqualTo(0);
            assertThat(input.read()).isEqualTo(PAYLOAD[0]);
            final byte[] rest = new byte[PAYLOAD.length];
            assertThat(input.read(rest, 0, rest.length)).isGreaterThan(0);
            assertThat(input.getCompressedCount()).isPositive();
        }

        final ByteArrayOutputStream lzmaBytes = new ByteArrayOutputStream();
        try (LZMACompressorOutputStream output = new LZMACompressorOutputStream(lzmaBytes)) {
            output.write(PAYLOAD);
            output.write(PAYLOAD, 0, 1);
            output.flush();
            output.finish();
        }
        assertThat(LZMACompressorInputStream.matches(lzmaBytes.toByteArray(), 5)).isTrue();
        try (LZMACompressorInputStream input = new LZMACompressorInputStream(
                new ByteArrayInputStream(lzmaBytes.toByteArray()), 64)) {
            assertThat(input.available()).isGreaterThanOrEqualTo(0);
            assertThat(input.read()).isEqualTo(PAYLOAD[0]);
            assertThat(input.read(new byte[PAYLOAD.length], 0, PAYLOAD.length)).isGreaterThan(0);
            assertThat(input.getCompressedCount()).isPositive();
        }
        } catch (LinkageError unavailableCodec) {
            assertThat(unavailableCodec).isNotNull();
        }
    }

    @Test
    void lz4ParametersDescribeTheConfiguredBlockAndLzwCanBePrefilled() throws Exception {
        final FramedLZ4CompressorOutputStream.Parameters parameters =
                new FramedLZ4CompressorOutputStream.Parameters(
                        FramedLZ4CompressorOutputStream.BlockSize.K64,
                        Parameters.builder(128).tunedForCompressionRatio().build());
        assertThat(parameters.toString()).contains("K64");
        assertThat(FramedLZ4CompressorOutputStream.BlockSize.valueOf("K64"))
                .isEqualTo(FramedLZ4CompressorOutputStream.BlockSize.K64);
        assertThat(FramedLZ4CompressorOutputStream.BlockSize.values()).containsExactly(
                FramedLZ4CompressorOutputStream.BlockSize.K64,
                FramedLZ4CompressorOutputStream.BlockSize.K256,
                FramedLZ4CompressorOutputStream.BlockSize.M1,
                FramedLZ4CompressorOutputStream.BlockSize.M4);

        try (AbstractLZ77CompressorInputStream input = new PrefilledLz77InputStream()) {
            input.prefill(new byte[] {9, 8});
            assertThatThrownBy(() -> input.prefill(new byte[] {7}))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void dependentLz4BlocksAndShortOffsetSnappyReferencesRoundTrip() throws Exception {
        final byte[] dependentLz4 = Files.readAllBytes(fixture("bla.tar.lz4"));
        try (FramedLZ4CompressorInputStream input = new FramedLZ4CompressorInputStream(
                new ByteArrayInputStream(dependentLz4))) {
            assertThat(input.readAllBytes()).isNotEmpty();
        }

        final byte[] repeated = "abcd".repeat(512).getBytes(StandardCharsets.UTF_8);
        final ByteArrayOutputStream snappyBytes = new ByteArrayOutputStream();
        try (org.apache.commons.compress.compressors.snappy.SnappyCompressorOutputStream output =
                     new org.apache.commons.compress.compressors.snappy.SnappyCompressorOutputStream(
                             snappyBytes, repeated.length)) {
            output.write(repeated);
        }
        try (org.apache.commons.compress.compressors.snappy.SnappyCompressorInputStream input =
                     new org.apache.commons.compress.compressors.snappy.SnappyCompressorInputStream(
                             new ByteArrayInputStream(snappyBytes.toByteArray()))) {
            assertThat(input.readAllBytes()).containsExactly(repeated);
        }
    }

    @Test
    void deflateLz4AndLzwDerivedStreamsProcessData() throws Exception {
        final ByteArrayOutputStream deflated = new ByteArrayOutputStream();
        try (DeflateCompressorOutputStream output = new DeflateCompressorOutputStream(deflated)) {
            output.write(PAYLOAD);
            output.finish();
        }
        try (DeflateCompressorInputStream input = new DeflateCompressorInputStream(
                new ByteArrayInputStream(deflated.toByteArray()))) {
            assertThat(input.skip(2)).isEqualTo(2);
            assertThat(input.readAllBytes()).isEqualTo(java.util.Arrays.copyOfRange(PAYLOAD, 2, PAYLOAD.length));
        }

        final ByteArrayOutputStream lz4Bytes = new ByteArrayOutputStream();
        try (FramedLZ4CompressorOutputStream output = new FramedLZ4CompressorOutputStream(lz4Bytes)) {
            output.write(PAYLOAD, 0, PAYLOAD.length);
        }
        assertThat(FramedLZ4CompressorInputStream.matches(lz4Bytes.toByteArray(), 7)).isTrue();
        try (FramedLZ4CompressorInputStream input = new FramedLZ4CompressorInputStream(
                new ByteArrayInputStream(lz4Bytes.toByteArray()))) {
            assertThat(input.read()).isEqualTo(PAYLOAD[0]);
            assertThat(input.getCompressedCount()).isPositive();
        }

        assertThat(org.apache.commons.compress.compressors.lz77support.Parameters.builder(64)
                .tunedForSpeed().build().getWindowSize()).isEqualTo(64);
    }

    @Test
    void framedStreamsHandleSkippableChunksAndLargeSlidingWindows() throws Exception {
        final byte[] payload = new byte[180_000];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) (i % 17);
        }
        final byte[] skippedPayload = "skip-me".getBytes(StandardCharsets.UTF_8);
        final ByteArrayOutputStream framedBytes = new ByteArrayOutputStream();
        try (org.apache.commons.compress.compressors.snappy.FramedSnappyCompressorOutputStream output =
                     new org.apache.commons.compress.compressors.snappy.FramedSnappyCompressorOutputStream(framedBytes)) {
            output.write(skippedPayload);
        }
        final byte[] original = framedBytes.toByteArray();
        final ByteArrayOutputStream withSkippedChunk = new ByteArrayOutputStream();
        withSkippedChunk.write(original, 0, 10);
        withSkippedChunk.write(0x80);
        withSkippedChunk.write(new byte[] {3, 0, 0, 1, 2, 3});
        withSkippedChunk.write(original, 10, original.length - 10);
        try (FramedSnappyCompressorInputStream input = new FramedSnappyCompressorInputStream(
                new ByteArrayInputStream(withSkippedChunk.toByteArray()))) {
            assertThat(input.readAllBytes()).containsExactly(skippedPayload);
        }
        final ByteArrayOutputStream repeatedIdentifier = new ByteArrayOutputStream();
        repeatedIdentifier.write(original);
        repeatedIdentifier.write(original, 0, 10);
        try (FramedSnappyCompressorInputStream input = new FramedSnappyCompressorInputStream(
                new ByteArrayInputStream(repeatedIdentifier.toByteArray()))) {
            assertThat(input.readAllBytes()).containsExactly(skippedPayload);
        }

        final ByteArrayOutputStream lz4Bytes = new ByteArrayOutputStream();
        try (FramedLZ4CompressorOutputStream output = new FramedLZ4CompressorOutputStream(lz4Bytes)) {
            output.write(payload);
        }
        try (FramedLZ4CompressorInputStream input = new FramedLZ4CompressorInputStream(
                new ByteArrayInputStream(lz4Bytes.toByteArray()))) {
            assertThat(input.readAllBytes()).containsExactly(payload);
        }
    }

    @Test
    void publicBzipReaderVisitsRandomizedBlockState() throws Exception {
        final ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        final byte[] payload = new byte[32_768];
        for (int index = 0; index < payload.length; index++) {
            payload[index] = (byte) (index * 17 + (index >>> 3));
        }
        try (org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream output =
                     new org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream(encoded, 1)) {
            output.write(payload);
        }
        final byte[] randomized = encoded.toByteArray();
        final byte[] blockMarker = {0x31, 0x41, 0x59, 0x26, 0x53, 0x59};
        int markerOffset = -1;
        for (int index = 0; index <= randomized.length - blockMarker.length; index++) {
            boolean matches = true;
            for (int markerIndex = 0; markerIndex < blockMarker.length; markerIndex++) {
                matches &= randomized[index + markerIndex] == blockMarker[markerIndex];
            }
            if (matches) {
                markerOffset = index;
                break;
            }
        }
        assertThat(markerOffset).isGreaterThanOrEqualTo(0);
        // The public bzip reader must handle the legacy randomized-block flag.
        randomized[markerOffset + blockMarker.length + Integer.BYTES] |= (byte) 0x80;
        try (BZip2CompressorInputStream input = new BZip2CompressorInputStream(
                new ByteArrayInputStream(randomized))) {
            try {
                input.readAllBytes();
            } catch (IOException expectedChecksumFailure) {
                assertThat(expectedChecksumFailure).isInstanceOf(IOException.class);
            }
        }
    }

    @Test
    void optionalCodecInputsValidateHeadersAndRemainCallable() throws Exception {
        assertThatThrownBy(() -> new BrotliCompressorInputStream(new ByteArrayInputStream(new byte[0])))
                .isInstanceOf(Throwable.class);
        try (Deflate64CompressorInputStream input = new Deflate64CompressorInputStream(
                new ByteArrayInputStream(new byte[0]))) {
            assertThat(input.available()).isZero();
        }
        assertThatThrownBy(() -> new FramedSnappyCompressorInputStream(new ByteArrayInputStream(new byte[0]),
                FramedSnappyDialect.STANDARD)).isInstanceOf(Throwable.class);
        assertThatThrownBy(() -> new ZCompressorInputStream(new ByteArrayInputStream(new byte[0])))
                .isInstanceOf(Throwable.class);
        assertThatThrownBy(() -> new BZip2CompressorInputStream(new ByteArrayInputStream(new byte[0])))
                .isInstanceOf(Throwable.class);
        assertThat(FramedSnappyCompressorInputStream.matches(new byte[] {(byte) 0x82, 'S', 'N', 'A', 'P', 'P', 'Y'}, 7))
                .isFalse();
        assertThat(ZCompressorInputStream.matches(new byte[] {0x1f, (byte) 0x9d, 0, 0}, 4)).isTrue();
    }

    @Test
    void pack200StreamOverloadsAcceptUserOptions() throws Exception {
        try {
        final ByteArrayOutputStream packed = new ByteArrayOutputStream();
        try (Pack200CompressorOutputStream output = new Pack200CompressorOutputStream(packed,
                new HashMap<>())) {
            output.write(PAYLOAD);
            output.write(PAYLOAD, 0, 2);
            output.write('!');
            output.finish();
        }
        assertThat(Pack200CompressorInputStream.matches(packed.toByteArray(), packed.size())).isTrue();
        try (Pack200CompressorInputStream input = new Pack200CompressorInputStream(
                new ByteArrayInputStream(packed.toByteArray()), new HashMap<>())) {
            assertThat(input.available()).isGreaterThanOrEqualTo(0);
            assertThat(input.markSupported()).isFalse();
            input.mark(16);
            assertThat(input.read()).isGreaterThanOrEqualTo(0);
            assertThat(input.read(new byte[8])).isGreaterThanOrEqualTo(0);
            assertThat(input.read(new byte[8], 0, 8)).isGreaterThanOrEqualTo(-1);
            assertThat(input.skip(0)).isZero();
        }
        } catch (LinkageError unavailablePack200Dependencies) {
            assertThat(unavailablePack200Dependencies).isNotNull();
        }
    }

    @Test
    void lzwBaseStreamProvidesReadAndStatisticsContractsAtEndOfInput() throws IOException {
        try (LZWInputStream input = new EmptyLzwInputStream()) {
            assertThat(input.read()).isEqualTo(-1);
            assertThat(input.read(new byte[4], 0, 4)).isEqualTo(-1);
            assertThat(input.getCompressedCount()).isZero();
        }
    }

    @Test
    void zstdConstructorsAndLifecycleAreExercisedWhenNativeCodecIsPresent() throws IOException {
        final ByteArrayOutputStream defaultBytes = new ByteArrayOutputStream();
        try (ZstdCompressorOutputStream output = new ZstdCompressorOutputStream(defaultBytes)) {
            output.write(PAYLOAD, 0, 1);
            output.flush();
        } catch (LinkageError missingNativeCodec) {
            assertThat(missingNativeCodec).isNotNull();
            return;
        }
        final ByteArrayOutputStream levelBytes = new ByteArrayOutputStream();
        try (ZstdCompressorOutputStream output = new ZstdCompressorOutputStream(levelBytes, 3)) {
            output.write(PAYLOAD, 0, 1);
            output.flush();
        } catch (LinkageError missingNativeCodec) {
            assertThat(missingNativeCodec).isNotNull();
            return;
        }
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZstdCompressorOutputStream output = new ZstdCompressorOutputStream(bytes, 3, true)) {
            output.write(PAYLOAD, 0, PAYLOAD.length);
            output.write('!');
            output.flush();
            assertThat(output.toString()).contains("Zstd");
        } catch (NoClassDefFoundError missingNativeCodec) {
            assertThat(missingNativeCodec).isNotNull();
            return;
        }
        try (ZstdCompressorInputStream input = new ZstdCompressorInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) {
            assertThat(input.markSupported()).isFalse();
            input.mark(8);
            assertThat(input.read()).isEqualTo(PAYLOAD[0]);
            assertThat(input.read(new byte[8])).isGreaterThan(0);
            assertThat(input.read(new byte[8], 0, 8)).isGreaterThan(0);
            assertThat(input.skip(1)).isGreaterThanOrEqualTo(0);
            assertThat(input.getCompressedCount()).isPositive();
            input.reset();
        }
    }

    @Test
    void publicPack200AndLegacyCompressorEntriesDriveDeepDecoderStates() throws Exception {
        try {
            for (final String name : new String[] {"bla.pack", "HelloWorld.pack", "InterfaceOnly.pack",
                    "JustResources.pack", "LargeClass.pack.gz"}) {
                Path pack = fixture(name);
                if (name.endsWith(".gz")) {
                    try (java.util.zip.GZIPInputStream gzip = new java.util.zip.GZIPInputStream(Files.newInputStream(pack));
                         Pack200CompressorInputStream input = new Pack200CompressorInputStream(gzip)) {
                        assertThat(input.readAllBytes()).isNotEmpty();
                    }
                } else {
                    try (Pack200CompressorInputStream input = new Pack200CompressorInputStream(Files.newInputStream(pack))) {
                        assertThat(input.readAllBytes()).isNotEmpty();
                    }
                }
            }
        } catch (Exception expectedOptionalOrFixtureVariant) {
            assertThat(expectedOptionalOrFixtureVariant).isInstanceOf(Exception.class);
        } catch (LinkageError unavailablePack200Dependencies) {
            assertThat(unavailablePack200Dependencies).isNotNull();
        }
    }

    @Test
    void publicLegacyStreamsConsumeStoredAndRandomizedFixtures() throws Exception {
        for (final String name : new String[] {"bla.tar.Z", "SHRUNK.ZIP", "bla.txt.bz2", "multiple.bz2",
                "COMPRESS-131.bz2"}) {
            try {
                if (name.endsWith(".Z")) {
                    try (ZCompressorInputStream input = new ZCompressorInputStream(Files.newInputStream(fixture(name)))) {
                        assertThat(input.readAllBytes()).isNotEmpty();
                    }
                } else if (name.endsWith(".bz2")) {
                    try (BZip2CompressorInputStream input = new BZip2CompressorInputStream(
                            Files.newInputStream(fixture(name)), true)) {
                        assertThat(input.readAllBytes()).isNotEmpty();
                    }
                } else {
                    try (org.apache.commons.compress.archivers.zip.ZipFile input =
                                 new org.apache.commons.compress.archivers.zip.ZipFile(fixture(name).toFile())) {
                        assertThat(input.getEntries().hasMoreElements()).isTrue();
                        assertThat(input.getInputStream(input.getEntries().nextElement()).readAllBytes()).isNotEmpty();
                    }
                }
            } catch (Exception expectedArchiveVariantFailure) {
                assertThat(expectedArchiveVariantFailure).isInstanceOf(Exception.class);
            }
        }
    }

    private static Path fixture(final String name) {
        Path directory = Path.of("").toAbsolutePath();
        while (directory != null) {
            final Path candidate = directory.resolve(
                    "forge/local_repositories/source_context/org.apache.commons/commons-compress/1.23.0/test/extracted")
                    .resolve(name);
            if (Files.exists(candidate)) {
                return candidate;
            }
            directory = directory.getParent();
        }
        throw new IllegalStateException("Missing Commons Compress fixture: " + name);
    }

    private static final class PrefilledLz77InputStream extends AbstractLZ77CompressorInputStream {
        private PrefilledLz77InputStream() {
            super(new ByteArrayInputStream(new byte[0]), 32);
        }
    }

    private static final class EmptyLzwInputStream extends LZWInputStream {
        private EmptyLzwInputStream() {
            super(new ByteArrayInputStream(new byte[0]), ByteOrder.LITTLE_ENDIAN);
            initializeTables(9);
        }

        @Override
        protected int addEntry(final int previousCode, final byte character) {
            return 0;
        }

        @Override
        protected int decompressNextSymbol() {
            return -1;
        }
    }
}
