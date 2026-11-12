/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_hazelcast.hazelcast;

import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.client.config.ClientConnectionStrategyConfig.ReconnectMode;
import com.hazelcast.client.config.XmlClientConfigBuilder;
import com.hazelcast.config.EvictionPolicy;
import com.hazelcast.config.InMemoryFormat;
import com.hazelcast.config.MaxSizePolicy;
import com.hazelcast.config.NearCacheConfig;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class XmlClientConfigBuilderTest {
    @Test
    void buildsClientConfigurationWithReplacersAndNearCache() {
        String xml = """
                <hazelcast-client xmlns="http://www.hazelcast.com/schema/client-config">
                    <config-replacers>
                        <replacer class-name="com_hazelcast.hazelcast.ClusterNameReplacer">
                            <properties>
                                <property name="coverage.cluster">xml-clients</property>
                            </properties>
                        </replacer>
                    </config-replacers>
                    <cluster-name>$CLUSTER{coverage.cluster}</cluster-name>
                    <instance-name>xml-reader</instance-name>
                    <network>
                        <cluster-members>
                            <address>127.0.0.1:5701</address>
                            <address>127.0.0.1:5702</address>
                        </cluster-members>
                        <connection-timeout>20000</connection-timeout>
                    </network>
                    <near-cache name="catalog">
                        <in-memory-format>OBJECT</in-memory-format>
                        <invalidate-on-change>true</invalidate-on-change>
                        <time-to-live-seconds>120</time-to-live-seconds>
                        <eviction size="37" max-size-policy="ENTRY_COUNT" eviction-policy="LFU"/>
                    </near-cache>
                    <connection-strategy async-start="true" reconnect-mode="ASYNC">
                        <connection-retry>
                            <cluster-connect-timeout-millis>30000</cluster-connect-timeout-millis>
                        </connection-retry>
                    </connection-strategy>
                </hazelcast-client>
                """;
        ClientConfig config = new XmlClientConfigBuilder(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))
                .build();

        assertThat(config.getClusterName()).isEqualTo("xml-clients");
        assertThat(config.getInstanceName()).isEqualTo("xml-reader");
        assertThat(config.getNetworkConfig().getAddresses()).containsExactly("127.0.0.1:5701", "127.0.0.1:5702");
        assertThat(config.getNetworkConfig().getConnectionTimeout()).isEqualTo(20000);
        NearCacheConfig nearCache = config.getNearCacheConfigMap().get("catalog");
        assertThat(nearCache).isNotNull();
        assertThat(nearCache.getInMemoryFormat()).isEqualTo(InMemoryFormat.OBJECT);
        assertThat(nearCache.isInvalidateOnChange()).isTrue();
        assertThat(nearCache.getTimeToLiveSeconds()).isEqualTo(120);
        assertThat(nearCache.getEvictionConfig().getSize()).isEqualTo(37);
        assertThat(nearCache.getEvictionConfig().getMaxSizePolicy()).isEqualTo(MaxSizePolicy.ENTRY_COUNT);
        assertThat(nearCache.getEvictionConfig().getEvictionPolicy()).isEqualTo(EvictionPolicy.LFU);
        assertThat(config.getConnectionStrategyConfig().isAsyncStart()).isTrue();
        assertThat(config.getConnectionStrategyConfig().getReconnectMode()).isEqualTo(ReconnectMode.ASYNC);
        assertThat(config.getConnectionStrategyConfig().getConnectionRetryConfig().getClusterConnectTimeoutMillis())
                .isEqualTo(30000);
    }

    @Test
    void loadsDefaultClientConfiguration() {
        ClientConfig config = new XmlClientConfigBuilder().build();

        assertThat(config.getClusterName()).isEqualTo("dev");
        assertThat(config.getConnectionStrategyConfig().isAsyncStart()).isFalse();
        assertThat(config.getConnectionStrategyConfig().getReconnectMode()).isEqualTo(ReconnectMode.ON);
    }
}
