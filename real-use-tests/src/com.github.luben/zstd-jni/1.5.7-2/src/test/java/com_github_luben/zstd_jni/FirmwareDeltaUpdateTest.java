/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_luben.zstd_jni;

import com.github.luben.zstd.Zstd;
import com.github.luben.zstd.ZstdBufferDecompressingStreamNoFinalizer;
import com.github.luben.zstd.ZstdCompressCtx;
import com.github.luben.zstd.ZstdDecompressCtx;
import com.github.luben.zstd.ZstdException;
import com.github.luben.zstd.ZstdInputStream;
import com.github.luben.zstd.ZstdOutputStream;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Delta-compresses binaries against a previous version with zstd-jni raw-content dictionaries
 * (patch-from): `ZstdCompressCtx.loadDict(byte[])` and `ZstdDecompressCtx.loadDict(byte[])` for
 * whole-image deltas, and `ZstdOutputStream.setDict(byte[])` with `setLong`, read back through
 * `ZstdInputStream.setDict(byte[])` and a packet-fed heap
 * `ZstdBufferDecompressingStreamNoFinalizer`.
 *
 * An over-the-air update server publishes every firmware release as a delta against the release
 * before it, using the old image itself as the dictionary, so a device downloads only what changed.
 * Devices that are several releases behind apply the chain of deltas in order; a device that
 * applies a delta to the wrong base image is stopped by the frame checksum. Gateways with little
 * memory stream the delta as it arrives in radio packets and never hold the whole compressed delta.
 */
class FirmwareDeltaUpdateTest {

    private static final int IMAGE_SIZE = 1 << 20;

    private static final int LEVEL = 6;

    private static final int WINDOW_LOG = 22;

    private static final int PACKET_SIZE = 1500;

    /** What zstd reports as the content size of a frame written without a pledged size. */
    private static final long CONTENT_SIZE_UNKNOWN = -1L;

    /** One published release: the delta a device downloads and the digest of the rebuilt image. */
    record Release(String version, String baseVersion, byte[] delta, int imageSize, String sha256) {
    }

    /** The update server: keeps every image and publishes each new one as a delta to the last. */
    static final class UpdateServer {

        private final Map<String, byte[]> images = new LinkedHashMap<>();

        private final Map<String, Release> releases = new LinkedHashMap<>();

        private String latest;

        void publishInitial(String version, byte[] image) {
            images.put(version, image);
            latest = version;
        }

        Release publish(String version, byte[] image) {
            byte[] base = images.get(latest);
            byte[] delta;
            try (ZstdCompressCtx ctx = new ZstdCompressCtx()) {
                ctx.setLevel(LEVEL).setChecksum(true).setWindowLog(WINDOW_LOG).loadDict(base);
                delta = ctx.compress(image);
            }
            Release release = new Release(version, latest, delta, image.length, sha256(image));
            images.put(version, image);
            releases.put(version, release);
            latest = version;
            return release;
        }

        Release release(String version) {
            return releases.get(version);
        }

        /** Streams the delta to `version` the way a gateway download is served. */
        byte[] streamDelta(String version) throws IOException {
            Release release = releases.get(version);
            ByteArrayOutputStream wire = new ByteArrayOutputStream();
            try (ZstdOutputStream out = new ZstdOutputStream(wire)) {
                out.setLevel(LEVEL).setChecksum(true).setLong(WINDOW_LOG)
                        .setDict(images.get(release.baseVersion()));
                byte[] image = images.get(version);
                for (int offset = 0; offset < image.length; offset += 64 * 1024) {
                    out.write(image, offset, Math.min(64 * 1024, image.length - offset));
                }
            }
            return wire.toByteArray();
        }
    }

    /** A device: holds its installed image and rebuilds the next one from a delta. */
    static final class Device {

        private byte[] installed;

        private String version;

        Device(String version, byte[] installed) {
            this.version = version;
            this.installed = installed;
        }

        void apply(Release release) {
            assertThat(release.baseVersion()).isEqualTo(version);
            byte[] image;
            try (ZstdDecompressCtx ctx = new ZstdDecompressCtx()) {
                ctx.loadDict(installed);
                image = ctx.decompress(release.delta(), release.imageSize());
            }
            assertThat(sha256(image)).isEqualTo(release.sha256());
            installed = image;
            version = release.version();
        }
    }

    /** Decompresses a delta whose bytes arrive one radio packet at a time. */
    static final class PacketFedDecoder extends ZstdBufferDecompressingStreamNoFinalizer {

        private final Deque<ByteBuffer> pending;

        private int packetsConsumed;

        PacketFedDecoder(Deque<ByteBuffer> packets) {
            super(packets.removeFirst());
            this.pending = packets;
            this.packetsConsumed = 1;
        }

        @Override
        protected ByteBuffer refill(ByteBuffer exhausted) {
            if (pending.isEmpty()) {
                return exhausted;
            }
            packetsConsumed++;
            return pending.removeFirst();
        }

        int packetsConsumed() {
            return packetsConsumed;
        }
    }

    @Test
    void devicesFollowAChainOfDeltasAndRejectTheWrongBase() {
        byte[] v1 = initialImage();
        byte[] v2 = nextImage(v1, 2);
        byte[] v3 = nextImage(v2, 3);

        UpdateServer server = new UpdateServer();
        server.publishInitial("1.0", v1);
        Release r2 = server.publish("2.0", v2);
        Release r3 = server.publish("3.0", v3);

        // A raw-content dictionary carries no ID, and the frame records the rebuilt image size.
        assertThat(Zstd.getDictIdFromFrame(r2.delta())).isZero();
        assertThat(Zstd.getFrameContentSize(r2.delta())).isEqualTo(v2.length);
        assertThat(Zstd.getFrameContentSize(r3.delta())).isEqualTo(v3.length);

        // The images are high-entropy, so only the old image as dictionary makes the update small.
        byte[] standalone = Zstd.compress(v2, LEVEL);
        assertThat(standalone.length).isGreaterThan(IMAGE_SIZE * 9 / 10);
        assertThat(r2.delta().length).isLessThan(IMAGE_SIZE / 20);
        assertThat(r3.delta().length).isLessThan(IMAGE_SIZE / 20);

        Device behind = new Device("1.0", v1);
        behind.apply(server.release("2.0"));
        behind.apply(server.release("3.0"));
        assertThat(behind.installed).isEqualTo(v3);
        assertThat(behind.version).isEqualTo("3.0");

        // Applying the 3.0 delta to the 1.0 image rebuilds the wrong bytes and fails the checksum.
        try (ZstdDecompressCtx wrongBase = new ZstdDecompressCtx()) {
            wrongBase.loadDict(v1);
            assertThatThrownBy(() -> wrongBase.decompress(r3.delta(), r3.imageSize()))
                    .isInstanceOf(ZstdException.class);
        }

        // Without any base image, the delta references content the decoder does not have.
        try (ZstdDecompressCtx noBase = new ZstdDecompressCtx()) {
            assertThatThrownBy(() -> noBase.decompress(r2.delta(), r2.imageSize()))
                    .isInstanceOf(ZstdException.class);
        }
    }

    @Test
    void gatewaysStreamTheDeltaAsItArrives() throws IOException {
        byte[] v1 = initialImage();
        byte[] v2 = nextImage(v1, 2);

        UpdateServer server = new UpdateServer();
        server.publishInitial("1.0", v1);
        Release release = server.publish("2.0", v2);
        byte[] streamed = server.streamDelta("2.0");

        // A streamed frame does not know its content size up front.
        assertThat(Zstd.getFrameContentSize(streamed)).isEqualTo(CONTENT_SIZE_UNKNOWN);
        assertThat(streamed.length).isLessThan(IMAGE_SIZE / 20);

        // A gateway with an InputStream-based downloader reads the delta through ZstdInputStream.
        ByteArrayOutputStream rebuilt = new ByteArrayOutputStream();
        try (InputStream in = new ZstdInputStream(new ByteArrayInputStream(streamed))
                .setDict(v1).setLongMax(WINDOW_LOG)) {
            in.transferTo(rebuilt);
        }
        assertThat(sha256(rebuilt.toByteArray())).isEqualTo(release.sha256());

        // A radio gateway feeds packets into the decoder and writes the image into a heap buffer.
        Deque<ByteBuffer> packets = new ArrayDeque<>();
        for (int offset = 0; offset < streamed.length; offset += PACKET_SIZE) {
            int length = Math.min(PACKET_SIZE, streamed.length - offset);
            packets.addLast(ByteBuffer.wrap(Arrays.copyOfRange(streamed, offset, offset + length)));
        }
        int packetCount = packets.size();
        ByteBuffer flash = ByteBuffer.allocate(release.imageSize());
        ByteBuffer chunk = ByteBuffer.allocate(
                ZstdBufferDecompressingStreamNoFinalizer.recommendedTargetBufferSize());
        try (PacketFedDecoder decoder = new PacketFedDecoder(packets)) {
            decoder.setDict(v1).setLongMax(WINDOW_LOG);
            while (decoder.hasRemaining()) {
                chunk.clear();
                decoder.read(chunk);
                chunk.flip();
                flash.put(chunk);
            }
            assertThat(decoder.packetsConsumed()).isEqualTo(packetCount);
        }
        assertThat(flash.position()).isEqualTo(v2.length);
        assertThat(flash.array()).isEqualTo(v2);

        // The same stream read against the wrong base image is rejected by the checksum.
        byte[] other = nextImage(v1, 7);
        assertThatThrownBy(() -> {
            try (InputStream in = new ZstdInputStream(new ByteArrayInputStream(streamed))
                    .setDict(other)) {
                in.transferTo(new ByteArrayOutputStream());
            }
        }).isInstanceOf(IOException.class);
    }

    private static byte[] initialImage() {
        byte[] image = new byte[IMAGE_SIZE];
        new Random(1L).nextBytes(image);
        return image;
    }

    /** Builds the next release: a patched region, an inserted routine, and a removed table. */
    private static byte[] nextImage(byte[] previous, long seed) {
        Random random = new Random(seed);
        byte[] patched = previous.clone();
        int patchAt = 100_000 + random.nextInt(50_000);
        byte[] patch = new byte[4096];
        random.nextBytes(patch);
        System.arraycopy(patch, 0, patched, patchAt, patch.length);

        int insertAt = 400_000 + random.nextInt(50_000);
        byte[] inserted = new byte[8192];
        random.nextBytes(inserted);
        int removeAt = 800_000 + random.nextInt(50_000);
        int removed = inserted.length;

        ByteArrayOutputStream next = new ByteArrayOutputStream(previous.length);
        next.write(patched, 0, insertAt);
        next.write(inserted, 0, inserted.length);
        next.write(patched, insertAt, removeAt - insertAt);
        next.write(patched, removeAt + removed, patched.length - removeAt - removed);
        return next.toByteArray();
    }

    private static String sha256(byte[] image) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(image));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
