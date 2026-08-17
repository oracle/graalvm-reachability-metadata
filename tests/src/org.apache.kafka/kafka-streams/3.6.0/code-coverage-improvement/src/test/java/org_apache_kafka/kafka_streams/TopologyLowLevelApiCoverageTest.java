/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.processor.AbstractProcessor;
import org.apache.kafka.streams.processor.To;
import org.apache.kafka.streams.state.Stores;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TopologyLowLevelApiCoverageTest {
    @Test
    @SuppressWarnings("deprecation")
    void wiresSourcesProcessorsSinksAndStateStores() {
        Topology topology = new Topology();
        topology.addSource("source", Serdes.String().deserializer(), Serdes.String().deserializer(), "input")
                .addProcessor("processor", () -> new AbstractProcessor<String, String>() {
                    @Override
                    public void process(String key, String value) {
                        context().forward(key, value, To.all());
                    }
                }, "source")
                .addStateStore(Stores.keyValueStoreBuilder(Stores.inMemoryKeyValueStore("store"),
                        Serdes.String(), Serdes.String()), "processor")
                .connectProcessorAndStateStores("processor", "store")
                .addSink("sink", "output", Serdes.String().serializer(), Serdes.String().serializer(), "processor");

        String description = topology.describe().toString();
        assertThat(description).contains("source", "processor", "store", "sink", "input", "output");
    }

    @Test
    void wiresModernProcessorAndDynamicSinkOverloads() {
        Topology topology = new Topology();
        topology.addSource("pattern-source", Pattern.compile("records-.*"))
                .addProcessor("modern", () -> new org.apache.kafka.streams.processor.api.Processor<String, String, String, String>() {
                    @Override
                    public void init(org.apache.kafka.streams.processor.api.ProcessorContext<String, String> context) { }

                    @Override
                    public void process(org.apache.kafka.streams.processor.api.Record<String, String> record) { }

                    @Override
                    public void close() { }
                }, "pattern-source")
                .addSink("dynamic", (key, value, context) -> "dynamic-output", "modern");
        assertThat(topology.describe().toString()).contains("records-.*", "dynamic");
    }

    @Test
    void rejectsDisconnectedAndDuplicateNodesClearly() {
        Topology topology = new Topology().addSource("source", "input");
        assertThatThrownBy(() -> topology.addSource("source", "other"))
                .isInstanceOf(org.apache.kafka.streams.errors.TopologyException.class)
                .hasMessageContaining("source");
        assertThatThrownBy(() -> topology.addSink("sink", "output", "missing"))
                .isInstanceOf(org.apache.kafka.streams.errors.TopologyException.class)
                .hasMessageContaining("missing");
    }
}
