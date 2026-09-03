/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TopologyTestDriver;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.RocksDBConfigSetter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rocksdb.CompressionType;
import org.rocksdb.Options;
import org.rocksdb.WALRecoveryMode;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class RocksDBGenericOptionsToDbOptionsColumnFamilyOptionsAdapterTest {

    private static final String INPUT_TOPIC = "rocks-options-input";
    private static final String STORE_NAME = "rocks-options-counts";
    private static final int MAX_OPEN_FILES = 64;
    private static final long WRITE_BUFFER_SIZE = 4L * 1024L * 1024L;

    @TempDir
    Path stateDirectory;

    @BeforeEach
    void resetConfigSetter() {
        TrackingRocksDBConfigSetter.configureCalls.set(0);
        TrackingRocksDBConfigSetter.closeCalls.set(0);
        TrackingRocksDBConfigSetter.configuredStoreName = null;
        TrackingRocksDBConfigSetter.observedMaxOpenFiles = 0;
        TrackingRocksDBConfigSetter.observedWriteBufferSize = 0L;
        TrackingRocksDBConfigSetter.observedCompressionType = null;
        TrackingRocksDBConfigSetter.walDirectoryWasIgnored = false;
        TrackingRocksDBConfigSetter.atomicFlushRemainedEnabled = false;
    }

    @Test
    void shouldApplyGenericOptionsToDbAndColumnFamilyOptions() {
        StreamsBuilder builder = new StreamsBuilder();
        builder.stream(INPUT_TOPIC, Consumed.with(Serdes.String(), Serdes.String()))
                .groupBy((key, region) -> region, Grouped.with(Serdes.String(), Serdes.String()))
                .count(Materialized.<String, Long, KeyValueStore<Bytes, byte[]>>as(STORE_NAME));

        Properties properties = new Properties();
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "rocks-options-test");
        properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "unused:9092");
        properties.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.StringSerde.class);
        properties.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.StringSerde.class);
        properties.put(StreamsConfig.STATE_DIR_CONFIG, stateDirectory.toString());
        properties.put(StreamsConfig.ROCKSDB_CONFIG_SETTER_CLASS_CONFIG, TrackingRocksDBConfigSetter.class);

        try (TopologyTestDriver driver = new TopologyTestDriver(builder.build(), properties)) {
            TestInputTopic<String, String> input = driver.createInputTopic(
                    INPUT_TOPIC,
                    Serdes.String().serializer(),
                    Serdes.String().serializer());
            input.pipeInput("alice", "europe");
            input.pipeInput("bob", "europe");

            KeyValueStore<String, Long> store = driver.getKeyValueStore(STORE_NAME);
            assertThat(store.get("europe")).isEqualTo(2L);
            assertThat(TrackingRocksDBConfigSetter.configureCalls).hasValue(1);
            assertThat(TrackingRocksDBConfigSetter.configuredStoreName).isEqualTo(STORE_NAME);
            assertThat(TrackingRocksDBConfigSetter.observedMaxOpenFiles).isEqualTo(MAX_OPEN_FILES);
            assertThat(TrackingRocksDBConfigSetter.observedWriteBufferSize).isEqualTo(WRITE_BUFFER_SIZE);
            assertThat(TrackingRocksDBConfigSetter.observedCompressionType)
                    .isEqualTo(CompressionType.LZ4_COMPRESSION);
            assertThat(TrackingRocksDBConfigSetter.walDirectoryWasIgnored).isTrue();
            assertThat(TrackingRocksDBConfigSetter.atomicFlushRemainedEnabled).isTrue();
        }

        assertThat(TrackingRocksDBConfigSetter.closeCalls).hasValue(1);
    }

    public static class TrackingRocksDBConfigSetter implements RocksDBConfigSetter {

        private static final AtomicInteger configureCalls = new AtomicInteger();
        private static final AtomicInteger closeCalls = new AtomicInteger();
        private static String configuredStoreName;
        private static int observedMaxOpenFiles;
        private static long observedWriteBufferSize;
        private static CompressionType observedCompressionType;
        private static boolean walDirectoryWasIgnored;
        private static boolean atomicFlushRemainedEnabled;

        @Override
        public void setConfig(String storeName, Options options, Map<String, Object> configs) {
            String initialWalDirectory = options.walDir();
            options.setMaxOpenFiles(MAX_OPEN_FILES);
            options.setWriteBufferSize(WRITE_BUFFER_SIZE);
            options.setCompressionType(CompressionType.LZ4_COMPRESSION);
            options.setWalDir("ignored-by-kafka-streams");
            options.setWalTtlSeconds(60L);
            options.setWalSizeLimitMB(1L);
            options.setWalBytesPerSync(1024L);
            options.setMaxTotalWalSize(1024L);
            options.setManualWalFlush(true);
            options.setWalRecoveryMode(WALRecoveryMode.AbsoluteConsistency);
            options.setAtomicFlush(false);

            configuredStoreName = storeName;
            observedMaxOpenFiles = options.maxOpenFiles();
            observedWriteBufferSize = options.writeBufferSize();
            observedCompressionType = options.compressionType();
            walDirectoryWasIgnored = Objects.equals(initialWalDirectory, options.walDir());
            atomicFlushRemainedEnabled = options.atomicFlush();
            configureCalls.incrementAndGet();
        }

        @Override
        public void close(String storeName, Options options) {
            closeCalls.incrementAndGet();
        }
    }
}
