/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_luben.zstd_jni;

import com.github.luben.zstd.RecyclingBufferPool;
import com.github.luben.zstd.Zstd;
import com.github.luben.zstd.ZstdIOException;
import com.github.luben.zstd.ZstdInputStream;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorInputStream;
import org.apache.commons.compress.compressors.CompressorOutputStream;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorInputStream;
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorOutputStream;
import org.apache.commons.compress.compressors.zstandard.ZstdUtils;
import org.junit.jupiter.api.Test;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises zstd-jni as the Zstandard provider behind Commons Compress's codec registry:
 * `CompressorStreamFactory` names and auto-detects zstd uploads by their magic number, refuses
 * zstd outside a codec allow-list, and re-encodes uploads through `ZstdCompressorOutputStream`
 * with checksummed frames closed on every flush.
 *
 * A backup ingest service accepts customer uploads compressed with whatever codec the client
 * had at hand. It sniffs the codec instead of trusting the file name, decodes the upload, and
 * re-archives it as zstd with a frame closed after each batch so that a partially uploaded
 * archive still yields every committed batch. Archives are checksummed, so a flipped bit in
 * storage surfaces as a checksum error instead of silently corrupted data.
 */
class UploadIngestCodecNegotiationTest {

    private static final int ARCHIVE_LEVEL = 19;

    private final CompressorStreamFactory factory = new CompressorStreamFactory();

    @Test
    void zstdIsRegisteredAsAvailableCodec() {
        assertThat(ZstdUtils.isZstdCompressionAvailable()).isTrue();
        assertThat(factory.getInputStreamCompressorNames()).contains(CompressorStreamFactory.ZSTANDARD);
        assertThat(factory.getOutputStreamCompressorNames()).contains(CompressorStreamFactory.ZSTANDARD);
    }

    @Test
    void uploadsAreDecodedByDetectedCodecRegardlessOfName() throws Exception {
        String ledger = ledger(new Random(11), 2500);
        byte[] original = ledger.getBytes(StandardCharsets.UTF_8);
        byte[] zstdUpload = compressWith(CompressorStreamFactory.ZSTANDARD, original);
        byte[] gzipUpload = compressWith(CompressorStreamFactory.GZIP, original);

        assertThat(zstdUpload.length).isLessThan(original.length / 3);
        assertThat(ZstdUtils.matches(zstdUpload, zstdUpload.length)).isTrue();
        assertThat(ZstdUtils.matches(gzipUpload, gzipUpload.length)).isFalse();
        // A streamed frame cannot pledge its size up front, so the header leaves it unknown.
        assertThat(Zstd.getFrameContentSize(zstdUpload)).isEqualTo(-1L);

        Upload zstd = ingest(zstdUpload);
        Upload gzip = ingest(gzipUpload);

        assertThat(zstd.codec()).isEqualTo(CompressorStreamFactory.ZSTANDARD);
        assertThat(zstd.compressedBytesRead()).isEqualTo(zstdUpload.length);
        assertThat(zstd.content()).isEqualTo(original);
        assertThat(gzip.codec()).isEqualTo(CompressorStreamFactory.GZIP);
        assertThat(gzip.content()).isEqualTo(original);
    }

    @Test
    void codecAllowListRejectsZstdForLegacyTenant() throws Exception {
        byte[] upload = compressWith(CompressorStreamFactory.ZSTANDARD,
                ledger(new Random(13), 200).getBytes(StandardCharsets.UTF_8));
        Set<String> legacyCodecs = Set.of(CompressorStreamFactory.GZIP, CompressorStreamFactory.BZIP2);

        assertThatThrownBy(() -> factory.createCompressorInputStream(
                new BufferedInputStream(new ByteArrayInputStream(upload)), legacyCodecs))
                .isInstanceOf(CompressorException.class)
                .hasMessageContaining("No Compressor found");

        Set<String> modernCodecs = Set.of(CompressorStreamFactory.GZIP, CompressorStreamFactory.ZSTANDARD);
        try (CompressorInputStream in = factory.createCompressorInputStream(
                new BufferedInputStream(new ByteArrayInputStream(upload)), modernCodecs)) {
            assertThat(in).isInstanceOf(ZstdCompressorInputStream.class);
            assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).startsWith("txn-000000 ");
        }
    }

    @Test
    void archiveClosesOneCheckedFramePerFlushedBatch() throws Exception {
        Random random = new Random(17);
        List<String> batches = List.of(ledger(random, 700), ledger(random, 900), ledger(random, 400));
        ByteArrayOutputStream archive = new ByteArrayOutputStream();
        List<Integer> committedSizes = new ArrayList<>();

        try (ZstdCompressorOutputStream out = new ZstdCompressorOutputStream(archive, ARCHIVE_LEVEL, true, true)) {
            for (String batch : batches) {
                out.write(batch.getBytes(StandardCharsets.UTF_8));
                out.flush();
                committedSizes.add(archive.size());
            }
        }
        byte[] stored = archive.toByteArray();
        assertThat(stored.length).isEqualTo(committedSizes.get(committedSizes.size() - 1));

        StringBuilder expected = new StringBuilder();
        for (int i = 0; i < batches.size(); i++) {
            expected.append(batches.get(i));
            byte[] cutAfterBatch = Arrays.copyOf(stored, committedSizes.get(i));
            assertThat(new String(readThroughCommons(cutAfterBatch), StandardCharsets.UTF_8))
                    .isEqualTo(expected.toString());
        }
        try (InputStream in = new ZstdInputStream(new ByteArrayInputStream(stored))) {
            assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo(expected.toString());
        }

        byte[] bitRot = stored.clone();
        bitRot[bitRot.length - 1] ^= 0x01;
        assertThatThrownBy(() -> readThroughCommons(bitRot))
                .isInstanceOfSatisfying(ZstdIOException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(Zstd.errChecksumWrong()));
        byte[] untouchedFirstBatch = Arrays.copyOf(bitRot, committedSizes.get(0));
        assertThat(new String(readThroughCommons(untouchedFirstBatch), StandardCharsets.UTF_8))
                .isEqualTo(batches.get(0));
    }

    @Test
    void archiveSkipsToRequestedLedgerOffset() throws Exception {
        String ledger = ledger(new Random(19), 1500);
        byte[] original = ledger.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream archive = new ByteArrayOutputStream();
        try (ZstdCompressorOutputStream out = new ZstdCompressorOutputStream(archive, ARCHIVE_LEVEL)) {
            out.write(original);
        }

        int offset = ledger.indexOf("txn-001000");
        assertThat(offset).isPositive();
        try (ZstdCompressorInputStream in = new ZstdCompressorInputStream(
                new ByteArrayInputStream(archive.toByteArray()), RecyclingBufferPool.INSTANCE)) {
            long skipped = 0;
            while (skipped < offset) {
                long step = in.skip(offset - skipped);
                assertThat(step).isPositive();
                skipped += step;
            }
            byte[] line = new byte["txn-001000".length()];
            assertThat(in.readNBytes(line, 0, line.length)).isEqualTo(line.length);
            assertThat(new String(line, StandardCharsets.UTF_8)).isEqualTo("txn-001000");
            assertThat(in.getCompressedCount()).isLessThanOrEqualTo(archive.size());
        }
    }

    private Upload ingest(byte[] upload) throws IOException, CompressorException {
        InputStream sniffable = new BufferedInputStream(new ByteArrayInputStream(upload));
        String codec = CompressorStreamFactory.detect(sniffable);
        try (CompressorInputStream in = factory.createCompressorInputStream(sniffable)) {
            byte[] content = in.readAllBytes();
            long compressedRead = in instanceof ZstdCompressorInputStream zstd ? zstd.getCompressedCount() : -1;
            return new Upload(codec, content, compressedRead);
        }
    }

    private byte[] compressWith(String codec, byte[] content) throws IOException, CompressorException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (CompressorOutputStream<?> out = factory.createCompressorOutputStream(codec, bytes)) {
            out.write(content);
        }
        return bytes.toByteArray();
    }

    private static byte[] readThroughCommons(byte[] archive) throws IOException {
        try (InputStream in = new ZstdCompressorInputStream(new ByteArrayInputStream(archive))) {
            return in.readAllBytes();
        }
    }

    private static String ledger(Random random, int lines) {
        StringBuilder text = new StringBuilder();
        String[] accounts = {"ACME-01", "GLOBEX-07", "INITECH-12", "UMBRELLA-3"};
        for (int i = 0; i < lines; i++) {
            text.append(String.format(Locale.ROOT, "txn-%06d %s %s %8.2f EUR%n", i,
                    accounts[random.nextInt(accounts.length)],
                    random.nextBoolean() ? "DEBIT " : "CREDIT",
                    random.nextInt(1_000_000) / 100.0));
        }
        return text.toString();
    }

    private record Upload(String codec, byte[] content, long compressedBytesRead) {
    }
}
