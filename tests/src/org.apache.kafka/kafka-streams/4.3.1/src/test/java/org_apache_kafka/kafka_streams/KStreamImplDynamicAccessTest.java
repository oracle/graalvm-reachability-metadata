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
import org.apache.kafka.streams.kstream.Branched;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Named;
import org.apache.kafka.streams.kstream.Predicate;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class KStreamImplDynamicAccessTest {

    @Test
    void shouldCreateNamedBranchStreamsFromPredicates() {
        StreamsBuilder builder = new StreamsBuilder();
        KStream<String, String> input = builder.stream(
                "kstream-impl-branch-input",
                Consumed.with(Serdes.String(), Serdes.String()));

        Predicate<String, String> startsWithA = (key, value) -> value != null && value.startsWith("a");
        Predicate<String, String> remainingValues = (key, value) -> true;
        Map<String, KStream<String, String>> branches = input
                .split(Named.as("kstream-impl-branch-"))
                .branch(startsWithA, Branched.as("a"))
                .branch(remainingValues, Branched.as("rest"))
                .noDefaultBranch();

        branches.get("kstream-impl-branch-a").to("kstream-impl-branch-a-output");
        branches.get("kstream-impl-branch-rest").to("kstream-impl-branch-rest-output");
        Topology topology = builder.build();

        assertThat(branches).containsOnlyKeys("kstream-impl-branch-a", "kstream-impl-branch-rest");
        assertThat(branches.get("kstream-impl-branch-a")).isNotSameAs(input);
        assertThat(branches.get("kstream-impl-branch-rest")).isNotSameAs(input);
        assertThat(topology.describe().toString())
                .contains("kstream-impl-branch")
                .contains("kstream-impl-branch-a-output")
                .contains("kstream-impl-branch-rest-output");
    }
}
