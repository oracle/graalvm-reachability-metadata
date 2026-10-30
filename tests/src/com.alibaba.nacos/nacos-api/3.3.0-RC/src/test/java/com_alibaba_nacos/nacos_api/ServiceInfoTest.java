/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba_nacos.nacos_api;

import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.api.naming.pojo.builder.InstanceBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ServiceInfoTest {
    @Test
    void parsesDiscoveryCacheKeys() {
        ServiceInfo service = ServiceInfo.fromKey("payments@@orders@@east");

        assertEquals("payments", service.getGroupName());
        assertEquals("orders", service.getName());
        assertEquals("east", service.getClusters());
        assertEquals("payments@@orders@@east", service.getKey());
        assertEquals("payments@@orders", service.getKeyWithoutClusters());
        assertEquals("payments%40%40orders@@east", service.getKeyEncoded());

        ServiceInfo unclustered = ServiceInfo.fromKey("payments@@orders");
        assertEquals("payments@@orders", unclustered.getKey());
    }

    @Test
    void requiresHealthyPositiveWeightHosts() {
        ServiceInfo service = new ServiceInfo("orders", "east");
        Instance host = InstanceBuilder.newBuilder().setIp("192.0.2.20").setPort(8080).build();

        assertFalse(service.validate());
        service.addHost(host);
        assertTrue(service.validate());
        host.setHealthy(false);
        assertFalse(service.validate());
        host.setHealthy(true);
        host.setWeight(0.0);
        assertFalse(service.validate());
        service.setAllIps(true);
        assertTrue(service.validate());
    }

    @Test
    void isolatesCachedHostsAndMetadataWhenCloning() {
        ServiceInfo service = ServiceInfo.fromKey("payments@@orders@@east");
        Instance host = InstanceBuilder.newBuilder()
                .setIp("192.0.2.21").setPort(8080).addMetadata("zone", "east").build();
        service.addHost(host);
        service.setChecksum("cache-checksum");
        service.setCacheMillis(30000L);
        service.setLastRefTime(System.currentTimeMillis());

        ServiceInfo copy = service.clone();
        Instance copiedHost = copy.getHosts().get(0);
        assertEquals(service.getKey(), copy.getKey());
        assertEquals("cache-checksum", copy.getChecksum());
        assertEquals(30000L, copy.getCacheMillis());
        assertEquals(service.getLastRefTime(), copy.getLastRefTime());
        assertNotSame(host, copiedHost);
        assertEquals(host, copiedHost);

        copiedHost.addMetadata("zone", "west");
        copiedHost.setPort(9090);
        assertEquals("east", host.getMetadata().get("zone"));
        assertEquals(8080, host.getPort());
        copy.getHosts().clear();
        assertEquals(1, copy.ipCount());
        copy.addHost(InstanceBuilder.newBuilder().setIp("192.0.2.22").setPort(8080).build());
        assertEquals(2, copy.ipCount());
        assertEquals(1, service.ipCount());
    }
}
