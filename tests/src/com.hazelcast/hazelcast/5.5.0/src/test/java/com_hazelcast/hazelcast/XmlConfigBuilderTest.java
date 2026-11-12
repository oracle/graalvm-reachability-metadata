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
import com.hazelcast.config.XmlConfigBuilder;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class XmlConfigBuilderTest {
    @Test
    void buildsMemberConfigurationWithReplacersAndReflectiveSetters() {
        String xml = """
                <hazelcast xmlns="http://www.hazelcast.com/schema/config">
                    <config-replacers>
                        <replacer class-name="com_hazelcast.hazelcast.ClusterNameReplacer">
                            <properties>
                                <property name="coverage.cluster">xml-members</property>
                            </properties>
                        </replacer>
                    </config-replacers>
                    <cluster-name>$CLUSTER{coverage.cluster}</cluster-name>
                    <executor-service name="workers">
                        <pool-size>3</pool-size>
                        <queue-capacity>23</queue-capacity>
                        <statistics-enabled>false</statistics-enabled>
                    </executor-service>
                    <durable-executor-service name="durable-workers">
                        <pool-size>2</pool-size>
                        <durability>1</durability>
                        <capacity>41</capacity>
                        <statistics-enabled>false</statistics-enabled>
                    </durable-executor-service>
                    <pn-counter name="visits">
                        <replica-count>3</replica-count>
                        <statistics-enabled>false</statistics-enabled>
                    </pn-counter>
                    <map name="events">
                        <event-journal enabled="true">
                            <capacity>128</capacity>
                            <time-to-live-seconds>60</time-to-live-seconds>
                        </event-journal>
                    </map>
                </hazelcast>
                """;
        Config config = new XmlConfigBuilder(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))).build();

        assertThat(config.getClusterName()).isEqualTo("xml-members");
        ExecutorConfig executor = config.getExecutorConfigs().get("workers");
        assertThat(executor).isNotNull();
        assertThat(executor.getName()).isEqualTo("workers");
        assertThat(executor.getPoolSize()).isEqualTo(3);
        assertThat(executor.getQueueCapacity()).isEqualTo(23);
        assertThat(executor.isStatisticsEnabled()).isFalse();
        DurableExecutorConfig durable = config.getDurableExecutorConfigs().get("durable-workers");
        assertThat(durable).isNotNull();
        assertThat(durable.getPoolSize()).isEqualTo(2);
        assertThat(durable.getDurability()).isEqualTo(1);
        assertThat(durable.getCapacity()).isEqualTo(41);
        assertThat(durable.isStatisticsEnabled()).isFalse();
        PNCounterConfig counter = config.getPNCounterConfigs().get("visits");
        assertThat(counter).isNotNull();
        assertThat(counter.getReplicaCount()).isEqualTo(3);
        assertThat(counter.isStatisticsEnabled()).isFalse();
        assertThat(config.getMapConfigs().get("events").getEventJournalConfig().isEnabled()).isTrue();
        assertThat(config.getMapConfigs().get("events").getEventJournalConfig().getCapacity()).isEqualTo(128);
        assertThat(config.getMapConfigs().get("events").getEventJournalConfig().getTimeToLiveSeconds()).isEqualTo(60);
    }

    @Test
    void loadsDefaultMemberConfiguration() {
        Config config = new XmlConfigBuilder().build();

        assertThat(config.getClusterName()).isEqualTo("dev");
        assertThat(config.getNetworkConfig().getPort()).isEqualTo(5701);
        assertThat(config.getNetworkConfig().isPortAutoIncrement()).isTrue();
        assertThat(config.getMapConfig("default").getBackupCount()).isEqualTo(1);
    }
}
