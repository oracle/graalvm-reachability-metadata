/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TopologyConfig;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.processor.Processor;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.state.StoreBuilder;
import org.apache.kafka.streams.state.Stores;
import org.junit.jupiter.api.Test;

import java.util.Properties;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises source, table, global-table, and global-store builder entry points. */
public class StreamsBuilderApiCoverageTest {
    @Test
    void shouldBuildPatternSourcesAndGlobalTables() {
        StreamsBuilder builder = new StreamsBuilder(new TopologyConfig("builder-api", config(), new Properties()));
        assertThat(builder.stream(Pattern.compile("builder-input-.*"))).isNotNull();
        assertThat(builder.stream(Pattern.compile("builder-consumed-.*"), Consumed.with(Serdes.String(), Serdes.String()))).isNotNull();
        assertThat(builder.table("builder-table", org.apache.kafka.streams.kstream.Materialized.with(Serdes.String(), Serdes.String()))).isNotNull();
        assertThat(builder.globalTable("builder-global-default")).isNotNull();
        assertThat(builder.globalTable("builder-global-consumed", Consumed.with(Serdes.String(), Serdes.String()))).isNotNull();
        assertThat(builder.globalTable("builder-global-materialized", org.apache.kafka.streams.kstream.Materialized.with(Serdes.String(), Serdes.String()))).isNotNull();
        assertThat(builder.globalTable("builder-global-both", Consumed.with(Serdes.String(), Serdes.String()),
                org.apache.kafka.streams.kstream.Materialized.with(Serdes.String(), Serdes.String()))).isNotNull();
        builder.addStateStore(store("builder-state"));
        builder.addGlobalStore(store("builder-global-store"), "builder-global-source", Consumed.with(Serdes.String(), Serdes.String()),
                () -> new NoopProcessor());
        assertThat(builder.build().describe().toString()).contains("builder-global");
    }

    @Test
    void shouldUseProcessorApiGlobalStoreRegistration() {
        StreamsBuilder builder = new StreamsBuilder();
        builder.addGlobalStore(store("builder-api-global-store"), "builder-api-global-source",
                Consumed.with(Serdes.String(), Serdes.String()),
                () -> new org.apache.kafka.streams.processor.api.Processor<String, String, Void, Void>() {
                    @Override public void init(org.apache.kafka.streams.processor.api.ProcessorContext<Void, Void> context) { }
                    @Override public void process(org.apache.kafka.streams.processor.api.Record<String, String> record) { }
                });
        assertThat(builder.build()).isNotNull();
    }

    private static StreamsConfig config() {
        Properties properties = new Properties();
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "builder-api");
        properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        properties.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        properties.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        return new StreamsConfig(properties);
    }

    private static StoreBuilder<?> store(String name) {
        return Stores.keyValueStoreBuilder(Stores.inMemoryKeyValueStore(name), Serdes.String(), Serdes.String()).withLoggingDisabled();
    }

    private static final class NoopProcessor implements Processor<String, String> {
        @Override public void init(ProcessorContext context) { }
        @Override public void process(String key, String value) { }
        @Override public void close() { }
    }
}
