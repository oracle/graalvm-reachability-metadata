/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_hazelcast.hazelcast;

import com.hazelcast.config.Config;
import com.hazelcast.config.DurableExecutorConfig;
import com.hazelcast.config.ExecutorConfig;
import com.hazelcast.config.PNCounterConfig;
import com.hazelcast.config.YamlConfigBuilder;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class YamlConfigBuilderTest {
    @Test
    void buildsMemberConfigurationWithReplacersAndReflectiveSetters() {
        String yaml = """
                hazelcast:
                  config-replacers:
                    replacers:
                      - class-name: com_hazelcast.hazelcast.ClusterNameReplacer
                        properties:
                          coverage.cluster: yaml-members
                  cluster-name: $CLUSTER{coverage.cluster}
                  executor-service:
                    workers:
                      pool-size: 4
                      queue-capacity: 29
                      statistics-enabled: false
                  durable-executor-service:
                    durable-workers:
                      pool-size: 3
                      durability: 2
                      capacity: 43
                      statistics-enabled: false
                  pn-counter:
                    visits:
                      replica-count: 4
                      statistics-enabled: false
                  map:
                    events:
                      event-journal:
                        enabled: true
                        capacity: 256
                        time-to-live-seconds: 90
                """;
        Config config = new YamlConfigBuilder(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8))).build();

        assertThat(config.getClusterName()).isEqualTo("yaml-members");
        ExecutorConfig executor = config.getExecutorConfigs().get("workers");
        assertThat(executor).isNotNull();
        assertThat(executor.getName()).isEqualTo("workers");
        assertThat(executor.getPoolSize()).isEqualTo(4);
        assertThat(executor.getQueueCapacity()).isEqualTo(29);
        assertThat(executor.isStatisticsEnabled()).isFalse();
        DurableExecutorConfig durable = config.getDurableExecutorConfigs().get("durable-workers");
        assertThat(durable).isNotNull();
        assertThat(durable.getPoolSize()).isEqualTo(3);
        assertThat(durable.getDurability()).isEqualTo(2);
        assertThat(durable.getCapacity()).isEqualTo(43);
        assertThat(durable.isStatisticsEnabled()).isFalse();
        PNCounterConfig counter = config.getPNCounterConfigs().get("visits");
        assertThat(counter).isNotNull();
        assertThat(counter.getReplicaCount()).isEqualTo(4);
        assertThat(counter.isStatisticsEnabled()).isFalse();
        assertThat(config.getMapConfigs().get("events").getEventJournalConfig().isEnabled()).isTrue();
        assertThat(config.getMapConfigs().get("events").getEventJournalConfig().getCapacity()).isEqualTo(256);
        assertThat(config.getMapConfigs().get("events").getEventJournalConfig().getTimeToLiveSeconds()).isEqualTo(90);
    }

    @Test
    void loadsDefaultMemberConfiguration() {
        Config config = new YamlConfigBuilder().build();

        assertThat(config.getClusterName()).isEqualTo("dev");
        assertThat(config.getNetworkConfig().getPort()).isEqualTo(5701);
        assertThat(config.getNetworkConfig().isPortAutoIncrement()).isTrue();
        assertThat(config.getMapConfig("default").getBackupCount()).isEqualTo(1);
    }
}
