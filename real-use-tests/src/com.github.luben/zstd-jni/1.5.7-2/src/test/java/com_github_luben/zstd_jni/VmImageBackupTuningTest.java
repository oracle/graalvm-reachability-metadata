/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_luben.zstd_jni;

import com.github.luben.zstd.EndDirective;
import com.github.luben.zstd.Zstd;
import com.github.luben.zstd.ZstdCompressCtx;
import com.github.luben.zstd.ZstdDecompressCtx;
import com.github.luben.zstd.ZstdException;
import com.github.luben.zstd.ZstdFrameProgression;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tunes zstd-jni compression through `ZstdCompressCtx` advanced parameters: long-distance matching
 * (`setLong`, `setEnableLongDistanceMatching` with `Zstd.ParamSwitch`, `setWindowLog`), explicit
 * strategy profiles, multi-threaded workers observed through `ZstdFrameProgression`, and headerless
 * frames built with `setMagicless`, `setContentSize` and `setDictID`, with the `ZstdException`
 * error codes and parameter bounds `Zstd` reports.
 *
 * A virtual-machine backup agent snapshots disk images whose blocks repeat megabytes apart, so it
 * widens the match window to deduplicate them; it streams large images through a pool of native
 * workers while reporting progress; and it stores the per-block index as headerless frames, since
 * every byte of the four-byte magic number and the frame header counts across millions of entries.
 */
class VmImageBackupTuningTest {

    private static final int MIB = 1 << 20;

    private static final int LEVEL = 3;

    /** One entry of the block index: which disk block lives where in the backup archive. */
    record BlockIndexEntry(long block, long archiveOffset, int length, String digest) {

        byte[] encode() {
            return (block + ";" + archiveOffset + ";" + length + ";" + digest).getBytes(StandardCharsets.UTF_8);
        }

        static BlockIndexEntry decode(byte[] bytes) {
            String[] fields = new String(bytes, StandardCharsets.UTF_8).split(";");
            return new BlockIndexEntry(Long.parseLong(fields[0]), Long.parseLong(fields[1]),
                    Integer.parseInt(fields[2]), fields[3]);
        }
    }

    @Test
    void wideWindowDeduplicatesBlocksThatRepeatMegabytesApart() {
        byte[] image = diskImageWithDistantDuplicate();

        byte[] defaultWindow;
        try (ZstdCompressCtx compressor = new ZstdCompressCtx().setLevel(LEVEL)) {
            defaultWindow = compressor.compress(image);
        }
        byte[] longMode;
        try (ZstdCompressCtx compressor = new ZstdCompressCtx().setLevel(LEVEL).setLong(24)) {
            longMode = compressor.compress(image);
        }
        byte[] explicitLdm;
        try (ZstdCompressCtx compressor = new ZstdCompressCtx()
                .setLevel(LEVEL)
                .setWindowLog(24)
                .setEnableLongDistanceMatching(Zstd.ParamSwitch.ENABLE)) {
            explicitLdm = compressor.compress(image);
        }

        assertThat(defaultWindow.length).isGreaterThan(image.length * 9 / 10);
        assertThat(longMode.length).isLessThan(defaultWindow.length * 6 / 10);
        assertThat(explicitLdm.length).isLessThan(defaultWindow.length * 6 / 10);

        try (ZstdDecompressCtx decompressor = new ZstdDecompressCtx()) {
            assertThat(Zstd.getFrameContentSize(longMode)).isEqualTo(image.length);
            assertThat(decompressor.decompress(longMode, image.length)).isEqualTo(image);
            assertThat(decompressor.decompress(explicitLdm, image.length)).isEqualTo(image);
        }
    }

    @Test
    void archivalStrategyOutcompressesTheFastProfile() {
        byte[] guestLog = guestSyslog(4_000);

        byte[] fast;
        try (ZstdCompressCtx compressor = new ZstdCompressCtx().setLevel(1).setStrategy(1)) {
            fast = compressor.compress(guestLog);
        }
        byte[] archival;
        try (ZstdCompressCtx compressor = new ZstdCompressCtx()
                .setLevel(19)
                .setStrategy(9)
                .setWindowLog(22)
                .setHashLog(22)
                .setChainLog(23)
                .setSearchLog(6)
                .setMinMatch(3)
                .setTargetLength(256)
                .setChecksum(true)) {
            archival = compressor.compress(guestLog);
        }

        assertThat(archival.length).isLessThan(fast.length);
        assertThat(Zstd.decompress(archival, guestLog.length)).isEqualTo(guestLog);
        assertThat(Zstd.decompress(fast, guestLog.length)).isEqualTo(guestLog);

        assertThatThrownBy(() -> new ZstdCompressCtx().setWindowLog(Zstd.windowLogMax() + 1))
                .isInstanceOfSatisfying(ZstdException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(Zstd.errParameterOutOfBound()));
        assertThat(Zstd.minCompressionLevel()).isLessThan(0);
        assertThat(Zstd.maxCompressionLevel()).isEqualTo(22);
        assertThat(Zstd.defaultCompressionLevel()).isEqualTo(3);
    }

    @Test
    void workerPoolStreamsALargeImageAndReportsProgress() {
        byte[] image = randomizedImage(8 * MIB, 7);
        ByteBuffer source = ByteBuffer.allocateDirect(image.length);
        source.put(image).flip();
        ByteBuffer chunkOut = ByteBuffer.allocateDirect((int) Zstd.compressBound(MIB));
        ByteArrayOutputStream archive = new ByteArrayOutputStream();

        List<Long> ingestedAfterEachChunk = new ArrayList<>();
        ZstdFrameProgression finished;
        try (ZstdCompressCtx compressor = new ZstdCompressCtx()
                .setLevel(LEVEL)
                .setWorkers(2)
                .setJobSize(MIB)
                .setOverlapLog(6)
                .setChecksum(true)) {
            compressor.setPledgedSrcSize(image.length);
            int chunk = MIB / 2;
            for (int offset = 0; offset < image.length; offset += chunk) {
                ByteBuffer slice = source.duplicate();
                slice.position(offset).limit(Math.min(image.length, offset + chunk));
                while (slice.hasRemaining()) {
                    compressor.compressDirectByteBufferStream(chunkOut, slice, EndDirective.CONTINUE);
                    drain(chunkOut, archive);
                }
                ZstdFrameProgression progression = compressor.getFrameProgression();
                ingestedAfterEachChunk.add(progression.getIngested());
                assertThat(progression.getConsumed()).isLessThanOrEqualTo(progression.getIngested());
                assertThat(progression.getFlushed()).isLessThanOrEqualTo(progression.getProduced());
            }
            ByteBuffer empty = ByteBuffer.allocateDirect(0);
            boolean done;
            do {
                done = compressor.compressDirectByteBufferStream(chunkOut, empty, EndDirective.END);
                drain(chunkOut, archive);
            } while (!done);
            finished = compressor.getFrameProgression();
        }

        assertThat(ingestedAfterEachChunk).hasSize(16);
        for (int index = 0; index < ingestedAfterEachChunk.size(); index++) {
            assertThat(ingestedAfterEachChunk.get(index)).isEqualTo((index + 1L) * MIB / 2);
        }
        assertThat(finished.getIngested()).isEqualTo(image.length);
        assertThat(finished.getConsumed()).isEqualTo(image.length);
        assertThat(finished.getProduced()).isEqualTo(archive.size());
        assertThat(finished.getFlushed()).isEqualTo(archive.size());
        assertThat(finished.getNbActiveWorkers()).isZero();
        assertThat(archive.size()).isLessThan(image.length / 2);

        byte[] frame = archive.toByteArray();
        assertThat(Zstd.getFrameContentSize(frame)).isEqualTo(image.length);
        assertThat(Zstd.decompress(frame, image.length)).isEqualTo(image);

        byte[] fourWorkers;
        try (ZstdCompressCtx compressor = new ZstdCompressCtx().setLevel(LEVEL).setWorkers(4).setJobSize(MIB)
                .setOverlapLog(6).setChecksum(true)) {
            fourWorkers = compressor.compress(image);
        }
        byte[] twoWorkers;
        try (ZstdCompressCtx compressor = new ZstdCompressCtx().setLevel(LEVEL).setWorkers(2).setJobSize(MIB)
                .setOverlapLog(6).setChecksum(true)) {
            twoWorkers = compressor.compress(image);
        }
        // Multi-threaded output depends on the job layout, never on how many workers ran the jobs.
        assertThat(fourWorkers).isEqualTo(twoWorkers);
        assertThat(Zstd.decompress(fourWorkers, image.length)).isEqualTo(image);
    }

    @Test
    void blockIndexIsStoredAsHeaderlessFrames() {
        List<BlockIndexEntry> entries = new ArrayList<>();
        for (long block = 0; block < 64; block++) {
            entries.add(new BlockIndexEntry(block * 4096, block * 3172 + 512, 2048 + (int) (block % 7) * 64,
                    "sha256-" + Long.toHexString(block * 0x9E3779B97F4A7C15L)));
        }

        try (ZstdCompressCtx standard = new ZstdCompressCtx().setLevel(LEVEL);
                ZstdCompressCtx sized = new ZstdCompressCtx().setLevel(LEVEL).setMagicless(true).setDictID(false);
                ZstdCompressCtx bare = new ZstdCompressCtx().setLevel(LEVEL).setMagicless(true)
                        .setContentSize(false).setDictID(false).setChecksum(false);
                ZstdDecompressCtx headerless = new ZstdDecompressCtx().setMagicless(true);
                ZstdDecompressCtx regular = new ZstdDecompressCtx()) {
            for (BlockIndexEntry entry : entries) {
                byte[] raw = entry.encode();
                byte[] standardFrame = standard.compress(raw);
                byte[] sizedFrame = sized.compress(raw);
                byte[] bareFrame = bare.compress(raw);

                assertThat(sizedFrame.length).isEqualTo(standardFrame.length - 4);
                assertThat(bareFrame.length).isLessThanOrEqualTo(sizedFrame.length);
                assertThat(Zstd.getFrameContentSize(sizedFrame, 0, sizedFrame.length, true)).isEqualTo(raw.length);
                assertThat(Zstd.getFrameContentSize(bareFrame, 0, bareFrame.length, true)).isEqualTo(-1L);

                assertThat(BlockIndexEntry.decode(headerless.decompress(sizedFrame, raw.length))).isEqualTo(entry);
                assertThat(BlockIndexEntry.decode(headerless.decompress(bareFrame, raw.length))).isEqualTo(entry);
                assertThatThrownBy(() -> regular.decompress(bareFrame, raw.length))
                        .isInstanceOfSatisfying(ZstdException.class,
                                error -> assertThat(error.getErrorCode()).isEqualTo(Zstd.errPrefixUnknown()));
            }
        }
    }

    private static void drain(ByteBuffer produced, ByteArrayOutputStream archive) {
        produced.flip();
        byte[] bytes = new byte[produced.remaining()];
        produced.get(bytes);
        archive.write(bytes, 0, bytes.length);
        produced.clear();
    }

    /** A 3 MiB block of random sectors, a zero gap, and the same block again further down the disk. */
    private static byte[] diskImageWithDistantDuplicate() {
        byte[] block = new byte[3 * MIB];
        new Random(42).nextBytes(block);
        byte[] image = new byte[2 * block.length + MIB / 4];
        System.arraycopy(block, 0, image, 0, block.length);
        System.arraycopy(block, 0, image, block.length + MIB / 4, block.length);
        return image;
    }

    /** Random sectors with a run of repeated text, so the workers have something to match. */
    private static byte[] randomizedImage(int size, long seed) {
        Random random = new Random(seed);
        byte[] image = new byte[size];
        byte[] sector = new byte[512];
        for (int offset = 0; offset < size; offset += sector.length) {
            if (random.nextInt(3) == 0) {
                random.nextBytes(sector);
            } else {
                byte[] text = ("sector " + (offset / 512 % 97) + " of a guest filesystem\n")
                        .getBytes(StandardCharsets.US_ASCII);
                for (int i = 0; i < sector.length; i++) {
                    sector[i] = text[i % text.length];
                }
            }
            System.arraycopy(sector, 0, image, offset, Math.min(sector.length, size - offset));
        }
        return image;
    }

    private static byte[] guestSyslog(int lines) {
        String[] units = {"sshd", "systemd", "kernel", "cron", "dockerd", "chronyd"};
        Random random = new Random(11);
        StringBuilder log = new StringBuilder();
        for (int line = 0; line < lines; line++) {
            log.append("2026-10-19T03:").append(String.format("%02d:%02d", line / 60 % 60, line % 60))
                    .append(" backup-vm-17 ").append(units[random.nextInt(units.length)])
                    .append('[').append(1000 + random.nextInt(50)).append("]: ")
                    .append("session ").append(random.nextInt(400)).append(" state=")
                    .append(random.nextBoolean() ? "opened" : "closed").append('\n');
        }
        return log.toString().getBytes(StandardCharsets.UTF_8);
    }
}
