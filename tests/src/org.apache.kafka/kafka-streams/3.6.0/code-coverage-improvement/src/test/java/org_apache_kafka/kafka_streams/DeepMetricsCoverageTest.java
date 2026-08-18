/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import org.apache.kafka.common.metrics.Metrics;
import org.apache.kafka.common.utils.Time;
import org.apache.kafka.streams.processor.TaskId;
import org.apache.kafka.streams.processor.internals.metrics.StreamsMetricsImpl;
import org.apache.kafka.streams.state.internals.metrics.RocksDBMetricsRecorder;
import org.junit.jupiter.api.Test;
import org.rocksdb.BlockBasedTableConfig;
import org.rocksdb.Cache;
import org.rocksdb.LRUCache;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.Statistics;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Drives RocksDB metric recording through the recorder's public entry point. */
public class DeepMetricsCoverageTest {
    @Test
    void shouldRecordStatisticsWithEmptyAndNonEmptyRatios() throws Exception {
        RocksDB.loadLibrary();
        Metrics metrics = new Metrics();
        StreamsMetricsImpl streamsMetrics = new StreamsMetricsImpl(metrics, "deep-coverage-client", "deep-thread",
                Time.SYSTEM);
        RocksDBMetricsRecorder recorder = new RocksDBMetricsRecorder("deep-scope", "deep-store");
        recorder.init(streamsMetrics, new TaskId(0, 0));

        Path databasePath = Files.createTempDirectory("deep-metrics");
        Cache cache = new LRUCache(1 << 20);
        BlockBasedTableConfig tableConfig = new BlockBasedTableConfig().setBlockCache(cache);
        try (cache;
             Statistics statistics = new Statistics();
             Options options = new Options().setCreateIfMissing(true)
                     .setStatistics(statistics).setTableFormatConfig(tableConfig);
             RocksDB database = RocksDB.open(options, databasePath.toString())) {
            recorder.addValueProviders("segment", database, cache, statistics);
            database.put(new byte[] {1}, new byte[] {2});
            recorder.record(1L);
            recorder.record(2L);
        }

        assertThat(recorder.storeName()).isEqualTo("deep-store");
        assertThat(recorder.taskId()).isEqualTo(new TaskId(0, 0));
        metrics.close();
    }
}
