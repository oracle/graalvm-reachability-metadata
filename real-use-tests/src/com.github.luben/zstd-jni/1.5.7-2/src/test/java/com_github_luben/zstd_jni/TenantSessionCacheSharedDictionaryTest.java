/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_luben.zstd_jni;

import com.github.luben.zstd.Zstd;
import com.github.luben.zstd.ZstdDictCompress;
import com.github.luben.zstd.ZstdDictDecompress;
import com.github.luben.zstd.ZstdException;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Shares off-heap zstd-jni dictionaries between worker threads: `Zstd.trainFromBufferDirect`
 * trains each tenant's dictionary from samples packed in a direct `ByteBuffer`, by-reference
 * `ZstdDictCompress` and `ZstdDictDecompress` digest that buffer without copying it, and workers
 * encode concurrently through the static `Zstd.compress`/`Zstd.decompress` dictionary overloads
 * on direct buffers and `Zstd.compressFastDict`/`Zstd.decompressFastDict` on heap arrays.
 *
 * A multi-tenant session cache keeps every user session as its own small compressed blob. Each
 * tenant's sessions share a shape that a trained dictionary captures, so the cache trains one
 * dictionary per tenant off-heap, digests it once and lets a pool of request threads use the
 * same digested dictionary at once. Blobs are inspected with `Zstd.getDictIdFromFrameBuffer`
 * and `Zstd.decompressedSize` before they are decoded, and a blob handed to the wrong tenant's
 * dictionary is refused with `Zstd.errDictionaryWrong` instead of yielding garbage.
 */
class TenantSessionCacheSharedDictionaryTest {

    private static final int DICTIONARY_CAPACITY = 8 * 1024;

    private static final int TRAINING_SAMPLES = 2_000;

    private static final int LEVEL = 5;

    private static final int WORKERS = 4;

    private static final int SESSIONS_PER_WORKER = 64;

    /** One tenant's trained dictionary, digested once and shared by every request thread. */
    static final class TenantCodec implements AutoCloseable {

        final String tenant;

        final ByteBuffer dictionary;

        final long dictionaryId;

        final ZstdDictCompress compressDictionary;

        final ZstdDictDecompress decompressDictionary;

        TenantCodec(String tenant, ByteBuffer dictionary) {
            this.tenant = tenant;
            this.dictionary = dictionary;
            this.dictionaryId = Zstd.getDictIdFromDictDirect(dictionary);
            this.compressDictionary = new ZstdDictCompress(dictionary, LEVEL, true);
            this.decompressDictionary = new ZstdDictDecompress(dictionary, true);
        }

        /// Trains a dictionary off-heap from session samples packed back to back in one buffer.
        static TenantCodec train(String tenant, List<byte[]> samples) {
            int total = samples.stream().mapToInt(sample -> sample.length).sum();
            ByteBuffer packed = ByteBuffer.allocateDirect(total);
            int[] sizes = new int[samples.size()];
            for (int i = 0; i < samples.size(); i++) {
                packed.put(samples.get(i));
                sizes[i] = samples.get(i).length;
            }
            packed.flip();
            ByteBuffer dictionary = ByteBuffer.allocateDirect(DICTIONARY_CAPACITY);
            long size = Zstd.trainFromBufferDirect(packed, sizes, dictionary, false, LEVEL);
            assertThat(Zstd.isError(size)).as(Zstd.getErrorName(size)).isFalse();
            dictionary.limit(Math.toIntExact(size));
            return new TenantCodec(tenant, dictionary);
        }

        /// Encodes a session on a request thread with the shared digested dictionary.
        ByteBuffer encode(byte[] session) {
            ByteBuffer source = ByteBuffer.allocateDirect(session.length).put(session).flip();
            return Zstd.compress(source, compressDictionary);
        }

        /// Decodes a blob after checking which dictionary wrote it and how large it inflates.
        byte[] decode(ByteBuffer blob) {
            long writtenWith = Zstd.getDictIdFromFrameBuffer(blob.duplicate());
            long contentSize = Zstd.decompressedSize(blob.duplicate());
            assertThat(writtenWith).isEqualTo(dictionaryId);
            ByteBuffer restored = Zstd.decompress(blob.duplicate(), decompressDictionary,
                    Math.toIntExact(contentSize));
            byte[] bytes = new byte[restored.remaining()];
            restored.get(bytes);
            return bytes;
        }

        @Override
        public void close() {
            compressDictionary.close();
            decompressDictionary.close();
        }
    }

    /** One stored session blob and the plain session it must decode to. */
    record StoredSession(String key, byte[] plain, ByteBuffer blob) {
    }

    @Test
    void requestThreadsShareOffHeapTenantDictionaries() throws Exception {
        try (TenantCodec shop = TenantCodec.train("shop", shopSessions(new Random(11), TRAINING_SAMPLES));
                TenantCodec bank = TenantCodec.train("bank", bankSessions(new Random(23), TRAINING_SAMPLES))) {
            assertThat(shop.dictionaryId).isNotZero().isNotEqualTo(bank.dictionaryId);
            assertThat(shop.compressDictionary.getByReferenceBuffer()).isSameAs(shop.dictionary);
            assertThat(shop.decompressDictionary.getByReferenceBuffer()).isSameAs(shop.dictionary);

            List<byte[]> incoming = shopSessions(new Random(97), WORKERS * SESSIONS_PER_WORKER);
            List<StoredSession> stored = encodeConcurrently(shop, incoming);
            assertThat(stored).hasSize(incoming.size());

            long dictionaryBytes = 0;
            long plainFrameBytes = 0;
            for (StoredSession session : stored) {
                assertThat(Zstd.getDictIdFromFrameBuffer(session.blob().duplicate()))
                        .isEqualTo(shop.dictionaryId);
                assertThat(Zstd.decompressedSize(session.blob().duplicate()))
                        .isEqualTo(session.plain().length);
                dictionaryBytes += session.blob().remaining();
                plainFrameBytes += Zstd.compress(session.plain(), LEVEL).length;
            }
            assertThat(dictionaryBytes * 2).isLessThan(plainFrameBytes);

            Map<String, byte[]> decoded = decodeConcurrently(shop, stored);
            for (StoredSession session : stored) {
                assertThat(decoded.get(session.key())).isEqualTo(session.plain());
            }

            ByteBuffer shopBlob = stored.get(0).blob();
            assertThatThrownBy(() -> Zstd.decompress(shopBlob.duplicate(), bank.decompressDictionary,
                    stored.get(0).plain().length))
                    .isInstanceOfSatisfying(ZstdException.class, failure ->
                            assertThat(failure.getErrorCode()).isEqualTo(Zstd.errDictionaryWrong()));
        }
    }

    @Test
    void heapPageSlotsUseTheFastDictionaryPath() {
        try (TenantCodec bank = TenantCodec.train("bank", bankSessions(new Random(5), TRAINING_SAMPLES))) {
            List<byte[]> sessions = bankSessions(new Random(61), 12);
            byte[] page = new byte[64 * 1024];
            int[] offsets = new int[sessions.size()];
            int[] lengths = new int[sessions.size()];
            int cursor = 0;
            for (int i = 0; i < sessions.size(); i++) {
                byte[] session = sessions.get(i);
                long written = Zstd.compressFastDict(page, cursor, session, 0, session.length,
                        bank.compressDictionary);
                assertThat(Zstd.isError(written)).as(Zstd.getErrorName(written)).isFalse();
                offsets[i] = cursor;
                lengths[i] = Math.toIntExact(written);
                cursor += lengths[i];
            }
            assertThat(cursor).isLessThan(sessions.stream().mapToInt(s -> s.length).sum() / 2);

            for (int i = 0; i < sessions.size(); i++) {
                byte[] slot = new byte[lengths[i]];
                System.arraycopy(page, offsets[i], slot, 0, lengths[i]);
                assertThat(Zstd.getDictIdFromFrame(slot)).isEqualTo(bank.dictionaryId);
                assertThat(Zstd.getFrameContentSize(slot)).isEqualTo(sessions.get(i).length);

                byte[] restored = new byte[sessions.get(i).length];
                long read = Zstd.decompressFastDict(restored, 0, page, offsets[i], lengths[i],
                        bank.decompressDictionary);
                assertThat(read).isEqualTo(restored.length);
                assertThat(new String(restored, StandardCharsets.UTF_8))
                        .isEqualTo(new String(sessions.get(i), StandardCharsets.UTF_8));
            }

            byte[] tooSmall = new byte[sessions.get(0).length / 2];
            int firstLength = lengths[0];
            assertThatThrownBy(() -> Zstd.decompressFastDict(tooSmall, 0, page, 0, firstLength,
                    bank.decompressDictionary))
                    .isInstanceOfSatisfying(ZstdException.class, failure ->
                            assertThat(failure.getErrorCode()).isEqualTo(Zstd.errDstSizeTooSmall()));
        }
    }

    /// Encodes sessions on a pool of request threads that all share one digested dictionary.
    private static List<StoredSession> encodeConcurrently(TenantCodec codec, List<byte[]> sessions)
            throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(WORKERS);
        try {
            List<Callable<List<StoredSession>>> batches = new ArrayList<>();
            for (int worker = 0; worker < WORKERS; worker++) {
                int first = worker * SESSIONS_PER_WORKER;
                batches.add(() -> {
                    List<StoredSession> batch = new ArrayList<>();
                    for (int i = first; i < first + SESSIONS_PER_WORKER; i++) {
                        byte[] session = sessions.get(i);
                        batch.add(new StoredSession(codec.tenant + "-" + i, session, codec.encode(session)));
                    }
                    return batch;
                });
            }
            List<StoredSession> stored = new ArrayList<>();
            for (Future<List<StoredSession>> batch : pool.invokeAll(batches)) {
                stored.addAll(batch.get());
            }
            return stored;
        } finally {
            pool.shutdown();
            assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }
    }

    /// Decodes every blob on a pool of request threads, keyed by session key.
    private static Map<String, byte[]> decodeConcurrently(TenantCodec codec, List<StoredSession> stored)
            throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(WORKERS);
        try {
            List<Callable<Map.Entry<String, byte[]>>> reads = new ArrayList<>();
            for (StoredSession session : stored) {
                reads.add(() -> Map.entry(session.key(), codec.decode(session.blob())));
            }
            Map<String, byte[]> decoded = new HashMap<>();
            for (Future<Map.Entry<String, byte[]>> read : pool.invokeAll(reads)) {
                decoded.put(read.get().getKey(), read.get().getValue());
            }
            return decoded;
        } finally {
            pool.shutdown();
            assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static List<byte[]> shopSessions(Random random, int count) {
        String[] locales = {"en-US", "de-DE", "fr-FR", "sr-RS", "ja-JP"};
        String[] skus = {"SKU-TSHIRT-RED", "SKU-MUG-WHITE", "SKU-HOODIE-GRY", "SKU-CAP-BLK", "SKU-SOCKS-3PK"};
        List<byte[]> sessions = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            StringBuilder cart = new StringBuilder();
            int lines = 1 + random.nextInt(4);
            for (int line = 0; line < lines; line++) {
                if (line > 0) {
                    cart.append(',');
                }
                cart.append(String.format("{\"sku\":\"%s\",\"quantity\":%d,\"unitPriceCents\":%d}",
                        skus[random.nextInt(skus.length)], 1 + random.nextInt(5), 499 + random.nextInt(5000)));
            }
            String session = String.format(
                    "{\"sessionId\":\"%08x-%04x\",\"userId\":%d,\"locale\":\"%s\",\"currency\":\"EUR\","
                            + "\"authenticated\":%b,\"cart\":[%s],\"lastSeenEpochSeconds\":%d,"
                            + "\"csrfToken\":\"%016x\"}",
                    random.nextInt(), random.nextInt(0x10000), 100_000 + random.nextInt(900_000),
                    locales[random.nextInt(locales.length)], random.nextBoolean(), cart,
                    1_760_000_000L + random.nextInt(1_000_000), random.nextLong());
            sessions.add(session.getBytes(StandardCharsets.UTF_8));
        }
        return sessions;
    }

    private static List<byte[]> bankSessions(Random random, int count) {
        String[] roles = {"RETAIL_CUSTOMER", "BUSINESS_OWNER", "ACCOUNTANT", "AUDITOR"};
        String[] factors = {"TOTP", "SMS_OTP", "HARDWARE_KEY", "PUSH_APPROVAL"};
        List<byte[]> sessions = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String session = String.format(
                    "<session customer=\"CUST%07d\" role=\"%s\" branch=\"BR-%03d\">"
                            + "<authentication factor=\"%s\" stepUp=\"%b\" issuedAt=\"%d\"/>"
                            + "<accounts primary=\"RS35%016d\" savings=\"RS35%016d\"/>"
                            + "<limits dailyTransferCents=\"%d\" remainingCents=\"%d\"/>"
                            + "<device fingerprint=\"%016x\" trusted=\"%b\"/></session>",
                    random.nextInt(10_000_000), roles[random.nextInt(roles.length)], random.nextInt(1000),
                    factors[random.nextInt(factors.length)], random.nextBoolean(),
                    1_760_000_000L + random.nextInt(1_000_000),
                    Math.floorMod(random.nextLong(), 10_000_000_000_000_000L),
                    Math.floorMod(random.nextLong(), 10_000_000_000_000_000L),
                    100_000 * (1 + random.nextInt(50)), random.nextInt(5_000_000),
                    random.nextLong(), random.nextBoolean());
            sessions.add(session.getBytes(StandardCharsets.UTF_8));
        }
        return sessions;
    }
}
