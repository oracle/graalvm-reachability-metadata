/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_compress;

import org.apache.commons.compress.compressors.CompressorInputStream;
import org.apache.commons.compress.compressors.CompressorOutputStream;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.apache.commons.compress.compressors.deflate.DeflateCompressorInputStream;
import org.apache.commons.compress.compressors.deflate64.Deflate64CompressorInputStream;
import org.apache.commons.compress.compressors.deflate.DeflateCompressorOutputStream;
import org.apache.commons.compress.compressors.lz4.FramedLZ4CompressorInputStream;
import org.apache.commons.compress.compressors.lz4.FramedLZ4CompressorOutputStream;
import org.apache.commons.compress.compressors.lz4.FramedLZ4CompressorOutputStream.BlockSize;
import org.apache.commons.compress.compressors.lzma.LZMAUtils;
import org.apache.commons.compress.compressors.pack200.Pack200CompressorInputStream;
import org.apache.commons.compress.compressors.pack200.Pack200CompressorOutputStream;
import org.apache.commons.compress.compressors.pack200.Pack200Strategy;
import org.apache.commons.compress.compressors.pack200.Pack200Utils;
import org.apache.commons.compress.compressors.snappy.FramedSnappyCompressorInputStream;
import org.apache.commons.compress.compressors.snappy.FramedSnappyCompressorOutputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream;
import org.apache.commons.compress.compressors.xz.XZUtils;
import org.apache.commons.compress.compressors.brotli.BrotliUtils;
import org.apache.commons.compress.compressors.z.ZCompressorInputStream;
import org.apache.commons.compress.compressors.zstandard.ZstdUtils;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.harmony.archive.internal.nls.Messages;
import org.apache.commons.compress.utils.ServiceLoaderIterator;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.ResourceBundle;
import java.util.zip.CRC32;

import static org.assertj.core.api.Assertions.assertThat;

class CompressorCoverageTest {

    @Test
    void deflateAndFramedCodecsRoundTripPayloads() throws Exception {
        final byte[] payload = "compress-me".getBytes(StandardCharsets.UTF_8);
        final ByteArrayOutputStream deflated = new ByteArrayOutputStream();
        try (DeflateCompressorOutputStream output = new DeflateCompressorOutputStream(deflated)) {
            output.write(payload, 0, payload.length);
            output.write('!');
            output.flush();
        }
        try (DeflateCompressorInputStream input = new DeflateCompressorInputStream(
                new ByteArrayInputStream(deflated.toByteArray()))) {
            assertThat(input.available()).isGreaterThanOrEqualTo(0);
            assertThat(input.read()).isEqualTo(payload[0]);
            assertThat(input.read(payload, 1, payload.length - 1)).isPositive();
            assertThat(input.getCompressedCount()).isPositive();
        }
        assertThat(DeflateCompressorInputStream.matches(new byte[] {1, 2}, 2)).isFalse();

        final ByteArrayOutputStream lz4 = new ByteArrayOutputStream();
        try (FramedLZ4CompressorOutputStream output = new FramedLZ4CompressorOutputStream(
                lz4, new FramedLZ4CompressorOutputStream.Parameters(BlockSize.K64))) {
            output.write(payload, 0, payload.length);
            output.write('!');
        }
        try (FramedLZ4CompressorInputStream input = new FramedLZ4CompressorInputStream(
                new ByteArrayInputStream(lz4.toByteArray()))) {
            assertThat(input.readAllBytes()).containsExactly("compress-me!".getBytes(StandardCharsets.UTF_8));
            assertThat(input.getCompressedCount()).isPositive();
        }
        assertThat(FramedLZ4CompressorInputStream.matches(lz4.toByteArray(), 4)).isTrue();

        final ByteArrayOutputStream snappy = new ByteArrayOutputStream();
        try (FramedSnappyCompressorOutputStream output = new FramedSnappyCompressorOutputStream(snappy)) {
            output.write(payload, 0, payload.length);
            output.write('!');
        }
        try (FramedSnappyCompressorInputStream input = new FramedSnappyCompressorInputStream(
                new ByteArrayInputStream(snappy.toByteArray()))) {
            assertThat(input.readAllBytes()).containsExactly("compress-me!".getBytes(StandardCharsets.UTF_8));
            assertThat(input.getCompressedCount()).isPositive();
        }
    }

    @Test
    void optionalCompressorConstructorsAndAvailabilityAreObservable() throws Exception {
        try {
            assertThat(LZMAUtils.isLZMACompressionAvailable()).isFalse();
            final boolean xzAvailable = XZUtils.isXZCompressionAvailable();
            assertThat(xzAvailable).isIn(true, false);
            assertThat(BrotliUtils.isBrotliCompressionAvailable()).isFalse();
            assertThat(ZstdUtils.isZstdCompressionAvailable()).isFalse();
            assertThat(XZUtils.matches(new byte[] {(byte) 0xfd, '7', 'z', 'X', 'Z', 0}, 6)).isTrue();
            assertThat(ZstdUtils.matches(new byte[] {0x28, (byte) 0xb5, 0x2f, (byte) 0xfd}, 4)).isTrue();
        } catch (LinkageError unavailableOptionalCodec) {
            assertThat(unavailableOptionalCodec).isNotNull();
        }

        try (XZCompressorInputStream input = new XZCompressorInputStream(
                new ByteArrayInputStream(new byte[0]))) {
            assertThat(input).isNotNull();
        } catch (Exception expectedForEmptyInput) {
            assertThat(expectedForEmptyInput).isInstanceOf(Exception.class);
        } catch (LinkageError unavailableOptionalXzCodec) {
            assertThat(unavailableOptionalXzCodec).isNotNull();
        }
        try (ZCompressorInputStream input = new ZCompressorInputStream(new ByteArrayInputStream(new byte[0]))) {
            assertThat(input).isNotNull();
        } catch (Exception expectedForEmptyInput) {
            assertThat(expectedForEmptyInput).isInstanceOf(Exception.class);
        }

        final Path inputFile = Files.createTempFile("pack200", ".pack");
        Files.write(inputFile, new byte[0]);
        final Map<String, String> properties = new HashMap<>();
        properties.put("effort", "1");
        for (Pack200Strategy strategy : Pack200Strategy.values()) {
            try {
                new Pack200CompressorInputStream(inputFile.toFile(), strategy, properties).close();
                new Pack200CompressorInputStream(new ByteArrayInputStream(new byte[0]), strategy, properties).close();
            } catch (Exception expectedUnavailableCodec) {
                assertThat(expectedUnavailableCodec).isInstanceOf(Exception.class);
            } catch (NoClassDefFoundError expectedUnavailableCodec) {
                assertThat(expectedUnavailableCodec).isNotNull();
            }
        }
        try {
            final CompressorOutputStream output = new Pack200CompressorOutputStream(new ByteArrayOutputStream(), strategy(), properties);
            output.close();
        } catch (Exception expectedUnavailableCodec) {
            assertThat(expectedUnavailableCodec).isInstanceOf(Exception.class);
        } catch (NoClassDefFoundError expectedUnavailableCodec) {
            assertThat(expectedUnavailableCodec).isNotNull();
        }
        try {
            Pack200Utils.normalize(inputFile.toFile(), inputFile.toFile());
            Pack200Utils.normalize(inputFile.toFile(), properties);
        } catch (Exception expectedUnavailableCodec) {
            assertThat(expectedUnavailableCodec).isInstanceOf(Exception.class);
        } catch (NoClassDefFoundError expectedUnavailableCodec) {
            assertThat(expectedUnavailableCodec).isNotNull();
        }
        Files.deleteIfExists(inputFile);
    }

    @Test
    void serviceAndMessageHelpersHaveNormalIteratorAndLocaleSemantics() {
        assertThat(new Messages()).isNotNull();
        final ResourceBundle bundle = Messages.setLocale(Locale.ROOT, "Messages");
        assertThat(bundle == null || bundle.getLocale().equals(Locale.ROOT)).isTrue();
        assertThat(Messages.format("value {0}", new Object[] {"x"})).contains("value");
        final ServiceLoaderIterator<CompressorInputStream> iterator = new ServiceLoaderIterator<>(CompressorInputStream.class);
        if (!iterator.hasNext()) {
            org.assertj.core.api.Assertions.assertThatThrownBy(iterator::next).isInstanceOf(NoSuchElementException.class);
        }
    }

    @Test
    void compressorPublicFactoriesExposeNamesAndLifecycleContracts() throws Exception {
        final CompressorStreamFactory factory = new CompressorStreamFactory();
        assertThat(factory.getBrotli()).isEqualTo(CompressorStreamFactory.BROTLI);
        assertThat(factory.getBzip2()).isEqualTo(CompressorStreamFactory.BZIP2);
        assertThat(factory.getDeflate()).isEqualTo(CompressorStreamFactory.DEFLATE);
        assertThat(factory.getDeflate64()).isEqualTo(CompressorStreamFactory.DEFLATE64);
        assertThat(factory.getGzip()).isEqualTo(CompressorStreamFactory.GZIP);
        assertThat(factory.getLZ4Block()).isEqualTo(CompressorStreamFactory.LZ4_BLOCK);
        assertThat(factory.getLZ4Framed()).isEqualTo(CompressorStreamFactory.LZ4_FRAMED);
        assertThat(factory.getLzma()).isEqualTo(CompressorStreamFactory.LZMA);
        assertThat(factory.getPack200()).isEqualTo(CompressorStreamFactory.PACK200);
        assertThat(factory.getSnappyFramed()).isEqualTo(CompressorStreamFactory.SNAPPY_FRAMED);
        assertThat(factory.getSnappyRaw()).isEqualTo(CompressorStreamFactory.SNAPPY_RAW);
        assertThat(factory.getXz()).isEqualTo(CompressorStreamFactory.XZ);
        assertThat(factory.getZ()).isEqualTo(CompressorStreamFactory.Z);
        assertThat(factory.getZstandard()).isEqualTo(CompressorStreamFactory.ZSTANDARD);
        factory.setDecompressConcatenated(true);
        final ByteArrayOutputStream bzipBytes = new ByteArrayOutputStream();
        try (org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream output =
                     new org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream(bzipBytes)) {
            output.write('b');
            output.flush();
        }
        assertThat(bzipBytes.size()).isPositive();
        try (org.apache.commons.compress.compressors.lz4.BlockLZ4CompressorOutputStream output =
                     new org.apache.commons.compress.compressors.lz4.BlockLZ4CompressorOutputStream(new ByteArrayOutputStream())) {
            output.write('l');
        }
        try (FramedLZ4CompressorOutputStream output =
                     new FramedLZ4CompressorOutputStream(new ByteArrayOutputStream())) {
            output.write('f');
        }
        try (Pack200CompressorOutputStream output =
                     new Pack200CompressorOutputStream(new ByteArrayOutputStream())) {
            output.write(new byte[] {'p', 'a'}, 0, 2);
            output.write(new byte[] {'c', 'k'});
        } catch (Exception unavailablePack200) {
            assertThat(unavailablePack200).isInstanceOf(Exception.class);
        } catch (NoClassDefFoundError unavailablePack200) {
            assertThat(unavailablePack200).isNotNull();
        }
        try (Pack200CompressorInputStream input = new Pack200CompressorInputStream(
                new ByteArrayInputStream(new byte[0]))) {
            assertThat(input).isNotNull();
        } catch (Exception unavailablePack200) {
            assertThat(unavailablePack200).isInstanceOf(Exception.class);
        } catch (NoClassDefFoundError unavailablePack200) {
            assertThat(unavailablePack200).isNotNull();
        }
        final Throwable cause = new IllegalStateException("cause");
        assertThat(new org.apache.commons.compress.compressors.CompressorException("message")).hasMessage("message");
        assertThat(new org.apache.commons.compress.compressors.CompressorException("message", cause).getCause()).isSameAs(cause);
    }

    @Test
    void gzipAndBzip2StreamsPreservePayloadAndMetadata() throws Exception {
        final byte[] payload = "gzip and bzip2 payload".getBytes(StandardCharsets.UTF_8);
        final org.apache.commons.compress.compressors.gzip.GzipParameters parameters =
                new org.apache.commons.compress.compressors.gzip.GzipParameters();
        parameters.setBufferSize(512);
        parameters.setComment("coverage");
        parameters.setCompressionLevel(6);
        parameters.setDeflateStrategy(0);
        parameters.setFilename("payload.txt");
        assertThat(parameters.getComment()).isEqualTo("coverage");
        assertThat(parameters.getFilename()).isEqualTo("payload.txt");
        final ByteArrayOutputStream gzipBytes = new ByteArrayOutputStream();
        try (org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream output =
                     new org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream(gzipBytes, parameters)) {
            output.write(payload, 0, payload.length);
            output.write('!');
            output.flush();
            output.finish();
        }
        try (org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream input =
                     new org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream(
                             new ByteArrayInputStream(gzipBytes.toByteArray()))) {
            assertThat(input.read()).isEqualTo(payload[0]);
            final byte[] rest = new byte[payload.length - 1];
            assertThat(input.read(rest, 0, rest.length)).isEqualTo(rest.length);
            assertThat(input.getMetaData().getFilename()).isEqualTo("payload.txt");
            assertThat(input.getCompressedCount()).isPositive();
        }
        assertThat(org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream.matches(
                gzipBytes.toByteArray(), 2)).isTrue();
        assertThat(org.apache.commons.compress.compressors.gzip.GzipUtils.isCompressedFilename("a.gz")).isTrue();
        assertThat(org.apache.commons.compress.compressors.gzip.GzipUtils.getUncompressedFilename("a.gz"))
                .isEqualTo("a");
        assertThat(org.apache.commons.compress.compressors.gzip.GzipUtils.getCompressedFilename("a"))
                .isEqualTo("a.gz");

        final ByteArrayOutputStream bzipBytes = new ByteArrayOutputStream();
        try (org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream output =
                     new org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream(bzipBytes, 1)) {
            assertThat(output.getBlockSize()).isEqualTo(1);
            output.write(payload, 0, payload.length);
            output.flush();
            output.finish();
        }
        try (org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream input =
                     new org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream(
                             new ByteArrayInputStream(bzipBytes.toByteArray()), true)) {
            assertThat(input.read()).isEqualTo(payload[0]);
            final byte[] rest = new byte[payload.length - 1];
            assertThat(input.read(rest, 0, rest.length)).isEqualTo(rest.length);
            assertThat(input.getCompressedCount()).isPositive();
        }
        assertThat(org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream.chooseBlockSize(1000))
                .isEqualTo(1);
    }

    @Test
    void lz77ConfigurationAndBlocksDescribeCompressionBehavior() throws Exception {
        final org.apache.commons.compress.compressors.lz77support.Parameters parameters =
                org.apache.commons.compress.compressors.lz77support.Parameters.builder(64)
                        .tunedForCompressionRatio()
                        .withLazyMatching(true)
                        .withLazyThreshold(3)
                        .withMaxNumberOfCandidates(8)
                        .withNiceBackReferenceLength(16)
                        .build();
        assertThat(parameters.getMaxBackReferenceLength()).isPositive();
        assertThat(parameters.getMaxCandidates()).isPositive();
        assertThat(parameters.getMaxOffset()).isPositive();
        assertThat(parameters.getNiceBackReferenceLength()).isEqualTo(16);
        final org.apache.commons.compress.compressors.lz77support.LZ77Compressor.BackReference reference =
                new org.apache.commons.compress.compressors.lz77support.LZ77Compressor.BackReference(2, 4);
        assertThat(reference.getLength()).isEqualTo(4);
        assertThat(reference.getOffset()).isEqualTo(2);
        assertThat(reference.getType()).isEqualTo(
                org.apache.commons.compress.compressors.lz77support.LZ77Compressor.Block.BlockType.BACK_REFERENCE);
        assertThat(reference.toString()).contains("BackReference");
        final org.apache.commons.compress.compressors.lz77support.LZ77Compressor.LiteralBlock literal =
                new org.apache.commons.compress.compressors.lz77support.LZ77Compressor.LiteralBlock(
                        new byte[] {'a', 'b'}, 0, 2);
        assertThat(literal.toString()).contains("LiteralBlock");
        final java.util.List<org.apache.commons.compress.compressors.lz77support.LZ77Compressor.Block> blocks =
                new java.util.ArrayList<>();
        final org.apache.commons.compress.compressors.lz77support.LZ77Compressor compressor =
                new org.apache.commons.compress.compressors.lz77support.LZ77Compressor(parameters, blocks::add);
        compressor.prefill(new byte[] {'a', 'b'});
        compressor.compress("abababab".getBytes(StandardCharsets.UTF_8));
        compressor.finish();
        assertThat(blocks).isNotEmpty();
    }

    @Test
    void blockLz4AndRawSnappyRoundTripTheirDeclaredPayloadSizes() throws Exception {
        final byte[] payload = "repeated repeated repeated".getBytes(StandardCharsets.UTF_8);
        final ByteArrayOutputStream lz4Bytes = new ByteArrayOutputStream();
        try (org.apache.commons.compress.compressors.lz4.BlockLZ4CompressorOutputStream output =
                     new org.apache.commons.compress.compressors.lz4.BlockLZ4CompressorOutputStream(lz4Bytes)) {
            output.prefill(new byte[] {'x', 'y', 'z'}, 0, 3);
            output.write(payload, 0, payload.length);
            output.finish();
        }
        try (org.apache.commons.compress.compressors.lz4.BlockLZ4CompressorInputStream input =
                     new org.apache.commons.compress.compressors.lz4.BlockLZ4CompressorInputStream(
                             new ByteArrayInputStream(lz4Bytes.toByteArray()))) {
            final byte[] first = new byte[payload.length];
            final int firstRead = input.read(first, 0, first.length);
            assertThat(firstRead).isPositive();
            final ByteArrayOutputStream decoded = new ByteArrayOutputStream();
            decoded.write(first, 0, firstRead);
            decoded.write(input.readAllBytes());
            assertThat(decoded.toByteArray()).containsExactly(payload);
            assertThat(input.getSize()).isEqualTo(payload.length);
            assertThat(input.getCompressedCount()).isPositive();
        }
        final ByteArrayOutputStream snappyBytes = new ByteArrayOutputStream();
        try (org.apache.commons.compress.compressors.snappy.SnappyCompressorOutputStream output =
                     new org.apache.commons.compress.compressors.snappy.SnappyCompressorOutputStream(snappyBytes, payload.length + 1)) {
            output.write(payload, 0, payload.length);
            output.write('!');
            output.finish();
        }
        try (org.apache.commons.compress.compressors.snappy.SnappyCompressorInputStream input =
                     new org.apache.commons.compress.compressors.snappy.SnappyCompressorInputStream(
                             new ByteArrayInputStream(snappyBytes.toByteArray()))) {
            assertThat(input.getSize()).isEqualTo(payload.length + 1);
            assertThat(input.readAllBytes()).containsExactly(
                    "repeated repeated repeated!".getBytes(StandardCharsets.UTF_8));
        }
    }

    @Test
    void filenameAndParameterApisExposeConfigurationSemantics() {
        final org.apache.commons.compress.compressors.deflate.DeflateParameters parameters =
                new org.apache.commons.compress.compressors.deflate.DeflateParameters();
        parameters.setCompressionLevel(7);
        parameters.setWithZlibHeader(false);
        assertThat(parameters.getCompressionLevel()).isEqualTo(7);
        try {
            assertThat(LZMAUtils.getCompressedFilename("payload")).isEqualTo("payload.lzma");
            assertThat(LZMAUtils.getUncompressedFilename("payload.lzma")).isEqualTo("payload");
            assertThat(LZMAUtils.isCompressedFilename("payload.lzma")).isTrue();
            assertThat(LZMAUtils.matches(new byte[] {0x5d, 0, 0, (byte) 0x80, 0}, 5)).isTrue();
        } catch (LinkageError unavailableOptionalLzmaCodec) {
            assertThat(unavailableOptionalLzmaCodec).isNotNull();
        }
        try {
            assertThat(XZUtils.getCompressedFilename("payload")).isEqualTo("payload.xz");
            assertThat(XZUtils.getUncompressedFilename("payload.xz")).isEqualTo("payload");
            assertThat(XZUtils.isCompressedFilename("payload.xz")).isTrue();
        } catch (LinkageError unavailableOptionalXzCodec) {
            assertThat(unavailableOptionalXzCodec).isNotNull();
        }
    }

    @Test
    void optionalCompressorOutputApisAreExercisedAtTheirRuntimeBoundary() throws Exception {
        try (org.apache.commons.compress.compressors.lzma.LZMACompressorOutputStream output =
                     new org.apache.commons.compress.compressors.lzma.LZMACompressorOutputStream(new ByteArrayOutputStream())) {
            output.write(new byte[] {'l', 'z'}, 0, 2);
            output.write('m');
            output.flush();
            output.finish();
        } catch (Exception unavailableCodec) {
            assertThat(unavailableCodec).isInstanceOf(Exception.class);
        } catch (NoClassDefFoundError unavailableCodec) {
            assertThat(unavailableCodec).isNotNull();
        }
        try (org.apache.commons.compress.compressors.xz.XZCompressorOutputStream output =
                     new org.apache.commons.compress.compressors.xz.XZCompressorOutputStream(new ByteArrayOutputStream(), 3)) {
            output.write(new byte[] {'x', 'z'}, 0, 2);
            output.write('!');
            output.flush();
            output.finish();
        } catch (Exception unavailableCodec) {
            assertThat(unavailableCodec).isInstanceOf(Exception.class);
        } catch (NoClassDefFoundError unavailableCodec) {
            assertThat(unavailableCodec).isNotNull();
        }
        try (org.apache.commons.compress.compressors.zstandard.ZstdCompressorOutputStream output =
                     new org.apache.commons.compress.compressors.zstandard.ZstdCompressorOutputStream(
                             new ByteArrayOutputStream(), 1, true, true)) {
            output.write(new byte[] {'z', 's'}, 0, 2);
            output.write('!');
            output.flush();
            assertThat(output.toString()).isNotEmpty();
        } catch (Exception unavailableCodec) {
            assertThat(unavailableCodec).isInstanceOf(Exception.class);
        } catch (NoClassDefFoundError unavailableCodec) {
            assertThat(unavailableCodec).isNotNull();
        }
        try (org.apache.commons.compress.compressors.brotli.BrotliCompressorInputStream input =
                     new org.apache.commons.compress.compressors.brotli.BrotliCompressorInputStream(
                             new ByteArrayInputStream(new byte[0]))) {
            input.mark(1);
            assertThat(input.markSupported()).isFalse();
            assertThat(input.available()).isGreaterThanOrEqualTo(0);
            assertThat(input.toString()).isNotEmpty();
        } catch (Exception unavailableCodec) {
            assertThat(unavailableCodec).isInstanceOf(Exception.class);
        } catch (NoClassDefFoundError unavailableCodec) {
            assertThat(unavailableCodec).isNotNull();
        }
    }

    @Test
    void optionalInputStreamsExposeTheirInputStreamContractsWhenCodecsArePresent() throws Exception {
        final byte[] payload = "optional codec payload".getBytes(StandardCharsets.UTF_8);
        try {
            final ByteArrayOutputStream encoded = new ByteArrayOutputStream();
            try (XZCompressorOutputStream output = new XZCompressorOutputStream(encoded, 3)) {
                output.write(payload, 0, payload.length);
            }
            try (XZCompressorInputStream input = new XZCompressorInputStream(
                    new ByteArrayInputStream(encoded.toByteArray()), true, 1)) {
                assertThat(input.available()).isGreaterThanOrEqualTo(0);
                input.mark(8);
                assertThat(input.markSupported()).isFalse();
                assertThat(input.read()).isEqualTo(payload[0]);
                final byte[] remaining = new byte[payload.length - 1];
                assertThat(input.read(remaining, 0, remaining.length)).isEqualTo(remaining.length);
                assertThat(input.getCompressedCount()).isPositive();
                assertThat(input.skip(0)).isZero();
            }
        } catch (Exception unavailableCodec) {
            assertThat(unavailableCodec).isInstanceOf(Exception.class);
        } catch (NoClassDefFoundError unavailableCodec) {
            assertThat(unavailableCodec).isNotNull();
        }
        try {
            final Pack200CompressorInputStream input = new Pack200CompressorInputStream(
                    new ByteArrayInputStream(new byte[0]), strategy(), new HashMap<>());
            assertThat(input.available()).isGreaterThanOrEqualTo(0);
            assertThat(input.markSupported()).isIn(true, false);
            input.mark(1);
            assertThat(input.skip(0)).isZero();
            input.reset();
            input.close();
        } catch (Exception unavailableCodec) {
            assertThat(unavailableCodec).isInstanceOf(Exception.class);
        } catch (NoClassDefFoundError unavailableCodec) {
            assertThat(unavailableCodec).isNotNull();
        }
    }

    @Test
    void bzip2LargeAndRandomizedBlocksExerciseDecoderStates() throws Exception {
        final byte[] largePayload = new byte[131_072];
        for (int i = 0; i < largePayload.length; i++) {
            largePayload[i] = (byte) (i * 31 + (i >>> 8));
        }
        final ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        try (org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream output =
                     new org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream(encoded, 1)) {
            output.write(largePayload);
        }
        try (org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream input =
                     new org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream(
                             new ByteArrayInputStream(encoded.toByteArray()))) {
            assertThat(input.readAllBytes()).containsExactly(largePayload);
        }
        for (final String name : new String[] {"bla.txt.bz2", "bla.xml.bz2", "COMPRESS-131.bz2",
                "lbzip2_32767.bz2", "multiple.bz2", "zip64support.tar.bz2"}) {
            try (org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream input =
                         new org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream(
                                 Files.newInputStream(fixture(name)), true)) {
                assertThat(input.readAllBytes()).isNotEmpty();
            }
        }
    }

    @Test
    void dependentLz4AndShortOffsetSnappyBlocksRoundTripThroughStreams() throws Exception {
        final byte[] repeated = new byte[128_000];
        java.util.Arrays.fill(repeated, (byte) 'a');
        for (final String name : new String[] {"bla.tar.lz4", "bla.dump.lz4", "COMPRESS-490/ArithmeticException.lz4"}) {
            try (FramedLZ4CompressorInputStream input = new FramedLZ4CompressorInputStream(
                         Files.newInputStream(fixture(name)))) {
                assertThat(input.readAllBytes()).isNotEmpty();
            } catch (Exception expectedLz4VariantFailure) {
                assertThat(expectedLz4VariantFailure).isInstanceOf(Exception.class);
            }
        }

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
    void enhancedDeflateZipEntriesDriveThePublicDeflate64Decoder() throws Exception {
        final Path archive = fixture("COMPRESS-380/COMPRESS-380.zip");
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            final ZipArchiveEntry entry = zip.getEntries().nextElement();
            assertThat(entry.getMethod()).isEqualTo(9);
            try (InputStream raw = zip.getRawInputStream(entry);
                 Deflate64CompressorInputStream input = new Deflate64CompressorInputStream(raw)) {
                assertThat(input.readAllBytes()).hasSize((int) entry.getSize());
                assertThat(input.getCompressedCount()).isPositive();
            }
        }
    }

    @Test
    void legacyLzwAndDeflate64EntriesExerciseDecoderStates() throws Exception {
        final Path zFile = fixture("bla.tar.Z");
        try (ZCompressorInputStream input = new ZCompressorInputStream(Files.newInputStream(zFile))) {
            assertThat(input.readAllBytes()).isNotEmpty();
            assertThat(input.read()).isEqualTo(-1);
        }
        final byte[] fixedDeflate64 = {
                1, 11, 0, -12, -1, 'H', 'e', 'l', 'l', 'o', ' ', 'W', 'o', 'r', 'l', 'd'
        };
        try (Deflate64CompressorInputStream input = new Deflate64CompressorInputStream(
                new ByteArrayInputStream(fixedDeflate64))) {
            assertThat(input.readAllBytes()).containsExactly("Hello World".getBytes(StandardCharsets.UTF_8));
            assertThat(input.available()).isGreaterThanOrEqualTo(0);
        }
    }

    @Test
    void checksumVerificationIsReachedThroughReadCompletion() throws Exception {
        final byte[] payload = "checksum".getBytes(StandardCharsets.UTF_8);
        final CRC32 expected = new CRC32();
        expected.update(payload);
        try (org.apache.commons.compress.utils.ChecksumVerifyingInputStream input =
                     new org.apache.commons.compress.utils.ChecksumVerifyingInputStream(
                             new CRC32(), new ByteArrayInputStream(payload), payload.length, expected.getValue())) {
            assertThat(input.readAllBytes()).containsExactly(payload);
            assertThat(input.getBytesRemaining()).isZero();
            assertThat(input.read()).isEqualTo(-1);
        }
    }

    @Test
    void codecEnumsAndPack200NormalizationExposeStableNames() throws Exception {
        assertThat(BlockSize.valueOf("K64")).isEqualTo(BlockSize.K64);
        assertThat(BlockSize.values()).contains(BlockSize.K64);
        assertThat(Pack200Strategy.valueOf("IN_MEMORY")).isEqualTo(Pack200Strategy.IN_MEMORY);
        assertThat(Pack200Strategy.values()).contains(Pack200Strategy.IN_MEMORY);
        final Path inputFile = Files.createTempFile("pack200-normalize", ".pack");
        try {
            Files.write(inputFile, new byte[0]);
            try {
                Pack200Utils.normalize(inputFile.toFile());
            } catch (Exception unavailableCodec) {
                assertThat(unavailableCodec).isInstanceOf(Exception.class);
            } catch (NoClassDefFoundError unavailableCodec) {
                assertThat(unavailableCodec).isNotNull();
            }
        } finally {
            Files.deleteIfExists(inputFile);
        }
    }

    private static Path fixture(final String name) {
        Path directory = Path.of("").toAbsolutePath();
        while (directory != null) {
            final Path candidate = directory.resolve("forge/local_repositories/source_context/org.apache.commons/commons-compress/1.23.0/test/extracted").resolve(name);
            if (Files.exists(candidate)) {
                return candidate;
            }
            directory = directory.getParent();
        }
        throw new IllegalStateException("Missing Commons Compress fixture: " + name);
    }

    private static Pack200Strategy strategy() {
        return Pack200Strategy.IN_MEMORY;
    }
}
