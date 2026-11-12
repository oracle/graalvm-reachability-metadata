/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_hazelcast.hazelcast;

import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.client.config.ClientConnectionStrategyConfig.ReconnectMode;
import com.hazelcast.client.config.YamlClientConfigBuilder;
import com.hazelcast.config.EvictionPolicy;
import com.hazelcast.config.InMemoryFormat;
import com.hazelcast.config.MaxSizePolicy;
import com.hazelcast.config.NearCacheConfig;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class YamlClientConfigBuilderTest {
    @Test
    void buildsClientConfigurationWithReplacersAndNearCache() {
        String yaml = """
                hazelcast-client:
                  config-replacers:
                    replacers:
                      - class-name: com_hazelcast.hazelcast.ClusterNameReplacer
                        properties:
                          coverage.cluster: yaml-clients
                  cluster-name: $CLUSTER{coverage.cluster}
                  instance-name: yaml-reader
                  network:
                    cluster-members:
                      - 127.0.0.1:5701
                      - 127.0.0.1:5702
                    connection-timeout: 20000
                  near-cache:
                    catalog:
                      in-memory-format: OBJECT
                      invalidate-on-change: true
                      time-to-live-seconds: 150
                      eviction:
                        size: 53
                        max-size-policy: ENTRY_COUNT
                        eviction-policy: LFU
                  connection-strategy:
                    async-start: true
                    reconnect-mode: ASYNC
                    connection-retry:
                      cluster-connect-timeout-millis: 30000
                """;
        ClientConfig config = new YamlClientConfigBuilder(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)))
                .build();

        assertThat(config.getClusterName()).isEqualTo("yaml-clients");
        assertThat(config.getInstanceName()).isEqualTo("yaml-reader");
        assertThat(config.getNetworkConfig().getAddresses()).containsExactly("127.0.0.1:5701", "127.0.0.1:5702");
        assertThat(config.getNetworkConfig().getConnectionTimeout()).isEqualTo(20000);
        NearCacheConfig nearCache = config.getNearCacheConfigMap().get("catalog");
        assertThat(nearCache).isNotNull();
        assertThat(nearCache.getInMemoryFormat()).isEqualTo(InMemoryFormat.OBJECT);
        assertThat(nearCache.isInvalidateOnChange()).isTrue();
        assertThat(nearCache.getTimeToLiveSeconds()).isEqualTo(150);
        assertThat(nearCache.getEvictionConfig().getSize()).isEqualTo(53);
        assertThat(nearCache.getEvictionConfig().getMaxSizePolicy()).isEqualTo(MaxSizePolicy.ENTRY_COUNT);
        assertThat(nearCache.getEvictionConfig().getEvictionPolicy()).isEqualTo(EvictionPolicy.LFU);
        assertThat(config.getConnectionStrategyConfig().isAsyncStart()).isTrue();
        assertThat(config.getConnectionStrategyConfig().getReconnectMode()).isEqualTo(ReconnectMode.ASYNC);
        assertThat(config.getConnectionStrategyConfig().getConnectionRetryConfig().getClusterConnectTimeoutMillis())
                .isEqualTo(30000);
    }

    @Test
    void loadsDefaultClientConfiguration() {
        ClientConfig config = new YamlClientConfigBuilder().build();

        assertThat(config.getClusterName()).isEqualTo("dev");
        assertThat(config.getConnectionStrategyConfig().isAsyncStart()).isFalse();
        assertThat(config.getConnectionStrategyConfig().getReconnectMode()).isEqualTo(ReconnectMode.ON);
    }
}
