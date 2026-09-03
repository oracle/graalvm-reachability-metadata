/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.TopologyTestDriver;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.state.KeyValueStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

public class KafkaStreamsTest {

    private static final String INPUT_TOPIC = "user-regions";
    private static final String OUTPUT_TOPIC = "region-counts-output";
    private static final String STORE_NAME = "region-counts";

    @TempDir
    Path stateDirectory;

    @Test
    void shouldCountUsersByRegion() {
        Serde<String> stringSerde = Serdes.String();
        Serde<Long> longSerde = Serdes.Long();
        StreamsBuilder builder = new StreamsBuilder();

        KTable<String, String> userRegions = builder.table(INPUT_TOPIC);
        KTable<String, Long> regionCounts = userRegions
                .groupBy((userId, region) -> KeyValue.pair(region, region))
                .count(Materialized.<String, Long, KeyValueStore<Bytes, byte[]>>as(STORE_NAME));
        KStream<String, Long> results = regionCounts.toStream().filter((region, count) -> count != null);
        results.to(OUTPUT_TOPIC, Produced.with(stringSerde, longSerde));

        try (TopologyTestDriver driver = new TopologyTestDriver(builder.build(), streamsProperties())) {
            TestInputTopic<String, String> input = driver.createInputTopic(
                    INPUT_TOPIC,
                    stringSerde.serializer(),
                    stringSerde.serializer());
            TestOutputTopic<String, Long> output = driver.createOutputTopic(
                    OUTPUT_TOPIC,
                    stringSerde.deserializer(),
                    longSerde.deserializer());

            input.pipeInput("alice", "asia");
            input.pipeInput("bob", "europe");

            KeyValueStore<String, Long> store = driver.getKeyValueStore(STORE_NAME);
            assertThat(store.get("asia")).isEqualTo(1L);
            assertThat(store.get("europe")).isEqualTo(1L);
            assertThat(output.readKeyValuesToList()).containsExactly(
                    KeyValue.pair("asia", 1L),
                    KeyValue.pair("europe", 1L));
        }
    }

    private Properties streamsProperties() {
        Properties properties = new Properties();
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "user-region-count-test");
        properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "unused:9092");
        properties.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.StringSerde.class);
        properties.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.StringSerde.class);
        properties.put(StreamsConfig.STATE_DIR_CONFIG, stateDirectory.toString());
        return properties;
    }
}
