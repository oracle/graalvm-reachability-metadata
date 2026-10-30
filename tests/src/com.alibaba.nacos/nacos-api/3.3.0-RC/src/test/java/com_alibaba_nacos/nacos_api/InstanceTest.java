/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba_nacos.nacos_api;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.PreservedMetadataKeys;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.builder.InstanceBuilder;
import com.alibaba.nacos.api.naming.utils.NamingUtils;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class InstanceTest {
    @Test
    void buildsAndValidatesServiceRegistration() throws NacosException {
        Instance instance = InstanceBuilder.newBuilder()
                .setInstanceId("orders-east")
                .setIp("192.0.2.10")
                .setPort(8080)
                .setServiceName("orders")
                .setWeight(2.5)
                .addMetadata("zone", "east")
                .build();

        NamingUtils.checkInstanceIsLegal(instance);

        assertEquals("orders-east", instance.getInstanceId());
        assertEquals("orders", instance.getServiceName());
        assertEquals("192.0.2.10:8080", instance.toInetAddr());
        assertEquals(Constants.DEFAULT_CLUSTER_NAME, instance.getClusterName());
        assertEquals(2.5, instance.getWeight());
        assertTrue(instance.isHealthy());
        assertTrue(instance.isEnabled());
        assertTrue(instance.isEphemeral());
        assertEquals("east", instance.getMetadata().get("zone"));
    }

    @Test
    void readsHeartbeatPolicyFromRegistrationMetadata() throws NacosException {
        Instance instance = InstanceBuilder.newBuilder()
                .setIp("192.0.2.11")
                .setPort(8081)
                .addMetadata(PreservedMetadataKeys.HEART_BEAT_INTERVAL, "10000")
                .addMetadata(PreservedMetadataKeys.HEART_BEAT_TIMEOUT, "30000")
                .addMetadata(PreservedMetadataKeys.IP_DELETE_TIMEOUT, "60000")
                .build();

        NamingUtils.checkInstanceIsLegal(instance);

        assertEquals(10000L, instance.getInstanceHeartBeatInterval());
        assertEquals(30000L, instance.getInstanceHeartBeatTimeOut());
        assertEquals(60000L, instance.getIpDeleteTimeout());
        assertTrue(instance.containsMetadata(PreservedMetadataKeys.HEART_BEAT_INTERVAL));
        assertFalse(instance.containsMetadata("zone"));

        instance.addMetadata(PreservedMetadataKeys.HEART_BEAT_INTERVAL, "default");
        assertEquals(Constants.DEFAULT_HEART_BEAT_INTERVAL, instance.getInstanceHeartBeatInterval());
    }

    @Test
    void rejectsInvalidRegistrationParameters() {
        Instance instance = InstanceBuilder.newBuilder().setIp("192.0.2.12").setPort(65536).build();
        assertThrows(NacosException.class, () -> NamingUtils.checkInstanceIsLegal(instance));

        instance.setPort(8080);
        instance.setEphemeral(false);
        assertThrows(NacosException.class, () -> NamingUtils.batchCheckInstanceIsLegal(List.of(instance)));
    }
}
