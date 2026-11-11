/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micrometer.micrometer_core;

import java.util.UUID;

import com.hazelcast.config.Config;
import com.hazelcast.config.MapConfig;
import com.hazelcast.config.NearCacheConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

public class HazelcastFixture implements AutoCloseable {

    final HazelcastInstance member;
    final IMap<String, String> map;

    public HazelcastFixture(boolean nearCache) {
        Config config = new Config().setClusterName("metrics-" + UUID.randomUUID());
        config.setProperty("hazelcast.logging.type", "none");
        config.setProperty("hazelcast.phone.home.enabled", "false");
        config.setProperty("hazelcast.shutdownhook.enabled", "false");
        config.setProperty("hazelcast.operation.thread.count", "2");
        config.setProperty("hazelcast.operation.generic.thread.count", "2");
        config.setProperty("hazelcast.io.input.thread.count", "1");
        config.setProperty("hazelcast.io.output.thread.count", "1");
        config.setProperty("hazelcast.event.thread.count", "1");
        config.setProperty("hazelcast.partition.count", "17");
        config.setProperty("hazelcast.operation.call.timeout.millis", "30000");
        config.getMetricsConfig().setEnabled(false);
        config.getNetworkConfig().setPort(0);
        config.getNetworkConfig().getInterfaces().setEnabled(true).addInterface("127.0.0.1");
        config.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(false);
        config.getNetworkConfig().getJoin().getTcpIpConfig().setEnabled(false);
        config.getNetworkConfig().getJoin().getAutoDetectionConfig().setEnabled(false);
        MapConfig mapConfig = new MapConfig("orders").setBackupCount(0).setStatisticsEnabled(true);
        if (nearCache) {
            mapConfig.setNearCacheConfig(new NearCacheConfig("orders").setCacheLocalEntries(true));
        }
        config.addMapConfig(mapConfig);
        member = Hazelcast.newHazelcastInstance(config);
        map = member.getMap("orders");
    }

    @Override
    public void close() {
        member.shutdown();
    }
}
