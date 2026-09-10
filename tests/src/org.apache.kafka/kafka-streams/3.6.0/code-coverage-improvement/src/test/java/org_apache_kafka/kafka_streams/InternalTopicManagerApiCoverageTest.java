/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import org.apache.kafka.common.utils.Time;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.processor.internals.InternalTopicManager;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Properties;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises the no-topic and no-op lifecycle contracts of the internal topic manager. */
public class InternalTopicManagerApiCoverageTest {
    @Test
    void shouldValidateAndSetUpAnEmptyInternalTopicPlan() {
        Properties properties = new Properties();
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "topic-manager-api");
        properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        StreamsConfig config = new StreamsConfig(properties);
        InternalTopicManager manager = new InternalTopicManager(Time.SYSTEM, null, config);

        assertThat(manager.validate(Map.of())).isNotNull();
        manager.setup(Map.of());
        assertThat(manager.getTopicPartitionInfo(Set.of())).isEmpty();
    }
}
