/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_luben.zstd_jni;

import com.github.luben.zstd.RecyclingBufferPool;
import com.github.luben.zstd.ZstdIOException;
import com.github.luben.zstd.ZstdInputStream;
import com.github.luben.zstd.ZstdOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Streams data through zstd-jni's `ZstdOutputStream` and `ZstdInputStream`: a tar bundle is
 * compressed on the fly, log sessions are appended to one file as concatenated frames, and
 * flushing with `setCloseFrameOnFlush` leaves a file that is readable after every flush.
 *
 * An operations team ships the logs of each host as a `.tar.zst` bundle, written in one pass
 * without staging the uncompressed tar on disk. On the hosts themselves, every service run
 * appends its lines to a `service.log.zst` that a crash may cut off at any point, so the logger
 * closes a frame whenever it flushes: a cut-off file still yields every flushed batch, and a
 * file cut inside its first frame is rejected.
 */
class LogBundleStreamingTest {

    private static final int LEVEL = 9;

    @TempDir
    Path directory;

    @Test
    void tarBundleIsCompressedAndRestoredInOnePass() throws IOException {
        Map<String, String> logs = new LinkedHashMap<>();
        logs.put("api-gateway/access.log", accessLog(new Random(3), 6000));
        logs.put("api-gateway/error.log", accessLog(new Random(5), 400).replace("INFO", "ERROR"));
        logs.put("billing/worker.log", accessLog(new Random(9), 3000));
        Path bundle = directory.resolve("host-17.tar.zst");

        try (OutputStream file = Files.newOutputStream(bundle);
                ZstdOutputStream zstd = new ZstdOutputStream(file, RecyclingBufferPool.INSTANCE)
                        .setLevel(LEVEL)
                        .setChecksum(true)
                        .setWorkers(2);
                TarArchiveOutputStream tar = new TarArchiveOutputStream(zstd)) {
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            for (Map.Entry<String, String> log : logs.entrySet()) {
                byte[] content = log.getValue().getBytes(StandardCharsets.UTF_8);
                TarArchiveEntry entry = new TarArchiveEntry(log.getKey());
                entry.setSize(content.length);
                tar.putArchiveEntry(entry);
                tar.write(content);
                tar.closeArchiveEntry();
            }
            tar.finish();
        }

        long plainBytes = logs.values().stream()
                .mapToLong(log -> log.getBytes(StandardCharsets.UTF_8).length)
                .sum();
        assertThat(Files.size(bundle) * 4).isLessThan(plainBytes);

        Map<String, String> restored = new LinkedHashMap<>();
        try (InputStream file = Files.newInputStream(bundle);
                ZstdInputStream zstd = new ZstdInputStream(file, RecyclingBufferPool.INSTANCE);
                TarArchiveInputStream tar = new TarArchiveInputStream(zstd)) {
            TarArchiveEntry entry;
            while ((entry = tar.getNextEntry()) != null) {
                restored.put(entry.getName(), new String(tar.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        assertThat(restored).containsExactlyEntriesOf(logs);
    }

    @Test
    void appendedSessionsAreReadBackAsOneStreamOfConcatenatedFrames() throws IOException {
        Path log = directory.resolve("service.log.zst");
        List<String> expected = new ArrayList<>();
        for (int session = 1; session <= 3; session++) {
            List<String> lines = sessionLines(session, 500 * session);
            expected.addAll(lines);
            try (OutputStream file = Files.newOutputStream(log, StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
                    Writer writer = new OutputStreamWriter(
                            new ZstdOutputStream(file, LEVEL).setChecksum(true), StandardCharsets.UTF_8)) {
                for (String line : lines) {
                    writer.write(line);
                    writer.write('\n');
                }
            }
        }

        assertThat(readLines(Files.readAllBytes(log))).containsExactlyElementsOf(expected);

        try (ZstdInputStream zstd = new ZstdInputStream(Files.newInputStream(log))) {
            long firstSessionBytes = sessionLines(1, 500).stream()
                    .mapToLong(line -> line.getBytes(StandardCharsets.UTF_8).length + 1)
                    .sum();
            assertThat(zstd.skip(firstSessionBytes)).isEqualTo(firstSessionBytes);
            String next = new BufferedReader(new InputStreamReader(zstd, StandardCharsets.UTF_8)).readLine();
            assertThat(next).isEqualTo(expected.get(500));
        }
    }

    @Test
    void everyFlushLeavesAReadableFileAndACutFrameIsRejected() throws IOException {
        Path log = directory.resolve("checkout.log.zst");
        List<Integer> flushedSizes = new ArrayList<>();
        List<String> lines = sessionLines(7, 900);

        try (OutputStream file = Files.newOutputStream(log);
                ZstdOutputStream zstd = new ZstdOutputStream(file, LEVEL)
                        .setChecksum(true)
                        .setCloseFrameOnFlush(true);
                Writer writer = new OutputStreamWriter(zstd, StandardCharsets.UTF_8)) {
            for (int batch = 0; batch < 3; batch++) {
                for (String line : lines.subList(batch * 300, (batch + 1) * 300)) {
                    writer.write(line);
                    writer.write('\n');
                }
                writer.flush();
                flushedSizes.add((int) Files.size(log));
            }
        }

        byte[] written = Files.readAllBytes(log);
        assertThat(flushedSizes).isSorted().doesNotHaveDuplicates();
        for (int batch = 0; batch < flushedSizes.size(); batch++) {
            byte[] afterFlush = Arrays.copyOf(written, flushedSizes.get(batch));
            assertThat(readLines(afterFlush)).containsExactlyElementsOf(lines.subList(0, (batch + 1) * 300));
        }

        byte[] cutInSecondBatch = Arrays.copyOf(written, (flushedSizes.get(0) + flushedSizes.get(1)) / 2);
        assertThat(readLines(cutInSecondBatch)).containsExactlyElementsOf(lines.subList(0, 300));

        byte[] cutInFirstBatch = Arrays.copyOf(written, flushedSizes.get(0) / 2);
        assertThatThrownBy(() -> readLines(cutInFirstBatch))
                .isInstanceOf(ZstdIOException.class)
                .hasMessageContaining("Truncated");
    }

    private static List<String> readLines(byte[] compressed) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ZstdInputStream(new ByteArrayInputStream(compressed)), StandardCharsets.UTF_8))) {
            List<String> lines = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
            return lines;
        }
    }

    private static List<String> sessionLines(int session, int count) {
        Random random = new Random(session);
        List<String> events = List.of("order placed", "payment authorized", "cart updated", "stock reserved");
        List<String> lines = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            lines.add("session=%d seq=%05d level=INFO event=\"%s\" user=u%04d latency_ms=%d".formatted(
                    session, index, events.get(random.nextInt(events.size())), random.nextInt(2000),
                    random.nextInt(900)));
        }
        return lines;
    }

    private static String accessLog(Random random, int count) {
        List<String> paths = List.of("/api/v1/orders", "/api/v1/users/me", "/api/v1/catalog?page=2",
                "/healthz", "/api/v1/payments");
        List<Integer> statuses = List.of(200, 200, 200, 201, 204, 304, 404, 500);
        StringBuilder log = new StringBuilder();
        long epochSecond = 1_760_000_000L;
        for (int index = 0; index < count; index++) {
            epochSecond += random.nextInt(3);
            log.append("%d INFO 10.0.%d.%d \"GET %s HTTP/1.1\" %d %d\n".formatted(
                    epochSecond, random.nextInt(4), random.nextInt(256),
                    paths.get(random.nextInt(paths.size())),
                    statuses.get(random.nextInt(statuses.size())), random.nextInt(40_000)));
        }
        return log.toString();
    }
}
