/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Named;
import org.apache.kafka.streams.kstream.Predicate;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class KStreamImplDynamicAccessTest {

    @Test
    @SuppressWarnings("deprecation")
    void shouldCreateTypedBranchStreamsFromPredicates() {
        StreamsBuilder builder = new StreamsBuilder();
        KStream<String, String> input = builder.stream(
                "kstream-impl-branch-input",
                Consumed.with(Serdes.String(), Serdes.String()));

        Predicate<String, String> startsWithA = (key, value) -> value != null && value.startsWith("a");
        Predicate<String, String> remainingValues = (key, value) -> true;
        KStream<String, String>[] branches = input.branch(
                Named.as("kstream-impl-branch"),
                startsWithA,
                remainingValues);

        branches[0].to("kstream-impl-branch-a-output");
        branches[1].to("kstream-impl-branch-rest-output");
        Topology topology = builder.build();

        assertThat(branches).hasSize(2);
        assertThat(branches[0]).isNotSameAs(input);
        assertThat(branches[1]).isNotSameAs(input);
        assertThat(topology.describe().toString())
                .contains("kstream-impl-branch")
                .contains("kstream-impl-branch-a-output")
                .contains("kstream-impl-branch-rest-output");
    }
}
