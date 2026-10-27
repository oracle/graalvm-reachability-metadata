/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_luben.zstd_jni;

import com.github.luben.zstd.Zstd;
import com.github.luben.zstd.ZstdBufferDecompressingStream;
import com.github.luben.zstd.ZstdDictCompress;
import com.github.luben.zstd.ZstdDictDecompress;
import com.github.luben.zstd.ZstdDictTrainer;
import com.github.luben.zstd.ZstdDirectBufferCompressingStream;
import com.github.luben.zstd.ZstdDirectBufferDecompressingStream;
import com.github.luben.zstd.ZstdIOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Primes zstd-jni's finalizer-managed `ByteBuffer` streams with a trained dictionary: an off-heap
 * dictionary from `ZstdDictTrainer.trainSamplesDirect` is set on a `ZstdDirectBufferCompressingStream`
 * through `setDict(ZstdDictCompress)`, and read back by a `ZstdDirectBufferDecompressingStream`
 * with `setDict(ZstdDictDecompress)` and a heap `ZstdBufferDecompressingStream` with
 * `setDict(byte[])`.
 *
 * A game server records every match as a replay of tick lines. Ticks of different matches look
 * alike, so the server trains a dictionary from earlier matches and primes every recording with
 * it; the recorder flushes a checkpoint every few ticks so spectators can follow a match that is
 * still running. The server validates finished replays from disk off-heap, spectator clients
 * decode the downloaded prefix on the heap, and a client without the dictionary is refused.
 */
class MatchReplayDictionaryStreamTest {

    private static final String[] PLAYERS = {"Raven-7", "Kestrel", "Nyx", "Ironclad", "Vesper", "Talon"};

    private static final String[] ACTIONS = {"MOVE", "FIRE", "RELOAD", "CROUCH", "JUMP", "USE_ITEM"};

    private static final String[] WEAPONS = {"PLASMA_RIFLE", "RAIL_GUN", "SHOTGUN", "SIDEARM"};

    private static final String[] ZONES = {"NORTH_SPIRE", "CANAL_DOCKS", "REACTOR_CORE", "EAST_GATE"};

    private static final int LEVEL = 6;

    private static final int DICTIONARY_BYTES = 4 * 1024;

    private static final int SAMPLE_BUFFER_BYTES = 1024 * 1024;

    private static final int TICKS_PER_CHECKPOINT = 25;

    private static final int DOWNLOAD_PACKET_BYTES = 512;

    /** Writes the replay to a file whenever the recorder flushes or its native output buffer fills. */
    static final class ReplayRecorder extends ZstdDirectBufferCompressingStream {

        private final FileChannel channel;

        private final List<Long> checkpoints = new ArrayList<>();

        ReplayRecorder(FileChannel channel, ZstdDictCompress dictionary) throws IOException {
            super(ByteBuffer.allocateDirect(recommendedOutputBufferSize()), LEVEL);
            this.channel = channel;
            setFinalize(false);
            setDict(dictionary);
        }

        @Override
        protected ByteBuffer flushBuffer(ByteBuffer toFlush) throws IOException {
            toFlush.flip();
            while (toFlush.hasRemaining()) {
                channel.write(toFlush);
            }
            toFlush.clear();
            return toFlush;
        }

        /** Appends one tick line from an off-heap buffer the game loop fills. */
        void record(String tick) throws IOException {
            byte[] line = (tick + "\n").getBytes(StandardCharsets.US_ASCII);
            ByteBuffer staged = ByteBuffer.allocateDirect(line.length);
            staged.put(line).flip();
            compress(staged);
            assertThat(staged.hasRemaining()).isFalse();
        }

        /** Makes everything recorded so far decodable and remembers where the file ends. */
        void checkpoint() throws IOException {
            flush();
            checkpoints.add(channel.position());
        }

        List<Long> checkpoints() {
            return checkpoints;
        }
    }

    /** Reads a finished replay from disk into off-heap buffers. */
    static final class ReplayFileReader extends ZstdDirectBufferDecompressingStream {

        private final FileChannel channel;

        ReplayFileReader(FileChannel channel, ByteBuffer source) {
            super(source);
            this.channel = channel;
            setFinalize(false);
        }

        @Override
        protected ByteBuffer refill(ByteBuffer toRefill) {
            toRefill.compact();
            try {
                channel.read(toRefill);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            toRefill.flip();
            return toRefill;
        }
    }

    /** Decodes a replay download packet by packet on the heap, as a spectator client does. */
    static final class SpectatorDecoder extends ZstdBufferDecompressingStream {

        private final Deque<ByteBuffer> packets;

        SpectatorDecoder(Deque<ByteBuffer> packets) {
            super(packets.removeFirst());
            this.packets = packets;
            setFinalize(false);
        }

        @Override
        protected ByteBuffer refill(ByteBuffer drained) {
            return packets.isEmpty() ? drained : packets.removeFirst();
        }

        boolean downloadExhausted() {
            return packets.isEmpty();
        }

        /** Decodes every tick the downloaded bytes contain, stopping where the download ends. */
        List<String> ticks() throws IOException {
            ByteArrayOutputStream decoded = new ByteArrayOutputStream();
            ByteBuffer target = ByteBuffer.allocate(256);
            while (hasRemaining()) {
                int produced = read(target);
                decoded.write(target.array(), 0, target.position());
                target.clear();
                if (produced == 0 && downloadExhausted()) {
                    break;
                }
            }
            return lines(decoded.toByteArray());
        }
    }

    @Test
    void dictionaryPrimedReplayRoundTripsThroughDirectAndHeapStreams(@TempDir Path directory) throws IOException {
        ByteBuffer dictionaryBuffer = trainDictionary();
        byte[] dictionaryBytes = new byte[dictionaryBuffer.remaining()];
        dictionaryBuffer.duplicate().get(dictionaryBytes);
        long dictionaryId = Zstd.getDictIdFromDictDirect(dictionaryBuffer);
        assertThat(dictionaryId).isNotZero();

        List<String> match = match(9_001L, 120);
        Path replay = directory.resolve("match-9001.replay.zst");
        List<Long> checkpoints;
        try (ZstdDictCompress dictionary = new ZstdDictCompress(dictionaryBuffer, LEVEL);
                FileChannel channel = FileChannel.open(replay, StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE);
                ReplayRecorder recorder = new ReplayRecorder(channel, dictionary)) {
            for (int i = 0; i < match.size(); i++) {
                recorder.record(match.get(i));
                if ((i + 1) % TICKS_PER_CHECKPOINT == 0) {
                    recorder.checkpoint();
                }
            }
            checkpoints = recorder.checkpoints();
        }
        byte[] file = Files.readAllBytes(replay);
        assertThat(checkpoints).hasSize(match.size() / TICKS_PER_CHECKPOINT).isSorted();
        assertThat(checkpoints.get(checkpoints.size() - 1)).isLessThan(file.length);
        assertThat(Zstd.getDictIdFromFrame(file)).isEqualTo(dictionaryId);

        List<String> validated;
        try (ZstdDictDecompress dictionary = new ZstdDictDecompress(dictionaryBuffer);
                FileChannel channel = FileChannel.open(replay, StandardOpenOption.READ);
                ReplayFileReader reader = new ReplayFileReader(channel, emptyDirect(4 * 1024))) {
            reader.setDict(dictionary);
            ByteBuffer target = ByteBuffer.allocateDirect(ZstdDirectBufferDecompressingStream.recommendedTargetBufferSize());
            ByteArrayOutputStream decoded = new ByteArrayOutputStream();
            while (reader.hasRemaining()) {
                reader.read(target);
                target.flip();
                byte[] chunk = new byte[target.remaining()];
                target.get(chunk);
                decoded.write(chunk);
                target.clear();
            }
            validated = lines(decoded.toByteArray());
        }
        assertThat(validated).containsExactlyElementsOf(match);

        int secondCheckpoint = Math.toIntExact(checkpoints.get(1));
        try (SpectatorDecoder spectator = new SpectatorDecoder(download(Arrays.copyOf(file, secondCheckpoint)))) {
            spectator.setDict(dictionaryBytes);
            List<String> following = spectator.ticks();
            assertThat(following).containsExactlyElementsOf(match.subList(0, 2 * TICKS_PER_CHECKPOINT));
            assertThat(spectator.hasRemaining()).as("the match is still running").isTrue();
        }

        try (SpectatorDecoder spectator = new SpectatorDecoder(download(file))) {
            spectator.setDict(dictionaryBytes);
            assertThat(spectator.ticks()).containsExactlyElementsOf(match);
            assertThat(spectator.hasRemaining()).as("the replay frame is complete").isFalse();
        }
    }

    @Test
    void clientWithoutTheDictionaryIsRefused(@TempDir Path directory) throws IOException {
        ByteBuffer dictionaryBuffer = trainDictionary();
        List<String> match = match(4_242L, 40);
        Path replay = directory.resolve("match-4242.replay.zst");
        try (ZstdDictCompress dictionary = new ZstdDictCompress(dictionaryBuffer, LEVEL);
                FileChannel channel = FileChannel.open(replay, StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE);
                ReplayRecorder recorder = new ReplayRecorder(channel, dictionary)) {
            for (String tick : match) {
                recorder.record(tick);
            }
        }
        byte[] file = Files.readAllBytes(replay);

        try (SpectatorDecoder spectator = new SpectatorDecoder(download(file))) {
            assertThatThrownBy(spectator::ticks)
                    .isInstanceOfSatisfying(ZstdIOException.class, e ->
                            assertThat(e.getErrorCode()).isEqualTo(Zstd.errDictionaryWrong()));
        }
    }

    @Test
    void primedRecordingIsSmallerThanAnUnprimedOne(@TempDir Path directory) throws IOException {
        ByteBuffer dictionaryBuffer = trainDictionary();
        List<String> match = match(77L, 30);

        Path primed = directory.resolve("primed.replay.zst");
        try (ZstdDictCompress dictionary = new ZstdDictCompress(dictionaryBuffer, LEVEL);
                FileChannel channel = FileChannel.open(primed, StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE);
                ReplayRecorder recorder = new ReplayRecorder(channel, dictionary)) {
            for (String tick : match) {
                recorder.record(tick);
            }
        }

        Path unprimed = directory.resolve("unprimed.replay.zst");
        try (FileChannel channel = FileChannel.open(unprimed, StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE);
                ZstdDirectBufferCompressingStream recorder = new ZstdDirectBufferCompressingStream(
                        ByteBuffer.allocateDirect(ZstdDirectBufferCompressingStream.recommendedOutputBufferSize()),
                        LEVEL) {
                    @Override
                    protected ByteBuffer flushBuffer(ByteBuffer toFlush) throws IOException {
                        toFlush.flip();
                        while (toFlush.hasRemaining()) {
                            channel.write(toFlush);
                        }
                        toFlush.clear();
                        return toFlush;
                    }
                }) {
            recorder.setFinalize(false);
            for (String tick : match) {
                ByteBuffer staged = ByteBuffer.allocateDirect(tick.length() + 1);
                staged.put((tick + "\n").getBytes(StandardCharsets.US_ASCII)).flip();
                recorder.compress(staged);
            }
        }

        long rawBytes = match.stream().mapToLong(tick -> tick.length() + 1L).sum();
        assertThat(Files.size(unprimed)).isLessThan(rawBytes);
        assertThat(Files.size(primed)).isLessThan(Files.size(unprimed));
    }

    /** Trains the replay dictionary off-heap from the ticks of earlier matches. */
    private static ByteBuffer trainDictionary() {
        ZstdDictTrainer trainer = new ZstdDictTrainer(SAMPLE_BUFFER_BYTES, DICTIONARY_BYTES);
        for (long seed = 1; seed <= 40; seed++) {
            for (String tick : match(seed, 60)) {
                assertThat(trainer.addSample(tick.getBytes(StandardCharsets.US_ASCII))).isTrue();
            }
        }
        ByteBuffer dictionary = trainer.trainSamplesDirect();
        assertThat(dictionary.isDirect()).isTrue();
        assertThat(dictionary.remaining()).isPositive().isLessThanOrEqualTo(DICTIONARY_BYTES);
        return dictionary;
    }

    /** Generates the tick lines of one match; the same seed always yields the same match. */
    private static List<String> match(long seed, int ticks) {
        Random random = new Random(seed);
        List<String> lines = new ArrayList<>(ticks);
        for (int tick = 0; tick < ticks; tick++) {
            String player = PLAYERS[random.nextInt(PLAYERS.length)];
            lines.add("tick=%05d player=%s pos=(%d,%d,%d) hp=%d action=%s weapon=%s zone=%s".formatted(
                    tick, player, random.nextInt(512), random.nextInt(512), random.nextInt(64),
                    random.nextInt(101), ACTIONS[random.nextInt(ACTIONS.length)],
                    WEAPONS[random.nextInt(WEAPONS.length)], ZONES[random.nextInt(ZONES.length)]));
        }
        return lines;
    }

    /** Splits a replay file into the heap packets a spectator client receives. */
    private static Deque<ByteBuffer> download(byte[] bytes) {
        Deque<ByteBuffer> packets = new ArrayDeque<>();
        for (int offset = 0; offset < bytes.length; offset += DOWNLOAD_PACKET_BYTES) {
            int length = Math.min(DOWNLOAD_PACKET_BYTES, bytes.length - offset);
            packets.add(ByteBuffer.wrap(bytes, offset, length).slice());
        }
        return packets;
    }

    private static ByteBuffer emptyDirect(int capacity) {
        return ByteBuffer.allocateDirect(capacity).flip();
    }

    private static List<String> lines(byte[] decoded) {
        String text = new String(decoded, StandardCharsets.US_ASCII);
        return text.isEmpty() ? List.of() : List.of(text.split("\n"));
    }
}
