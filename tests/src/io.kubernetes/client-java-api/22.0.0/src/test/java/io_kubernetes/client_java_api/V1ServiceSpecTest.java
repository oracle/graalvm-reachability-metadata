/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_kubernetes.client_java_api;

import io.kubernetes.client.openapi.models.V1ServiceSpec;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class V1ServiceSpecTest {
    @Test
    void readsAndWritesDualStackLoadBalancerConfiguration() throws Exception {
        V1ServiceSpec spec = V1ServiceSpec.fromJson(ModelJson.SERVICE_SPEC);
        assertThat(spec.getAllocateLoadBalancerNodePorts()).isTrue();
        assertThat(spec.getClusterIP()).isEqualTo("10.96.0.10");
        assertThat(spec.getClusterIPs()).containsExactly("10.96.0.10", "fd00::10");
        assertThat(spec.getExternalIPs()).containsExactly("192.0.2.20");
        assertThat(spec.getExternalName()).isEqualTo("web.example.test");
        assertThat(spec.getExternalTrafficPolicy()).isEqualTo("Local");
        assertThat(spec.getHealthCheckNodePort()).isEqualTo(30100);
        assertThat(spec.getInternalTrafficPolicy()).isEqualTo("Cluster");
        assertThat(spec.getIpFamilies()).containsExactly("IPv4", "IPv6");
        assertThat(spec.getIpFamilyPolicy()).isEqualTo("PreferDualStack");
        assertThat(spec.getLoadBalancerClass()).isEqualTo("example.test/balancer");
        assertThat(spec.getLoadBalancerIP()).isEqualTo("192.0.2.10");
        assertThat(spec.getLoadBalancerSourceRanges()).containsExactly("192.0.2.0/24");
        assertThat(spec.getPorts()).hasSize(1);
        assertThat(spec.getPorts().get(0).getPort()).isEqualTo(80);
        assertThat(spec.getPublishNotReadyAddresses()).isTrue();
        assertThat(spec.getSelector()).containsEntry("app", "web");
        assertThat(spec.getSessionAffinity()).isEqualTo("ClientIP");
        assertThat(spec.getSessionAffinityConfig().getClientIP().getTimeoutSeconds()).isEqualTo(600);
        assertThat(spec.getTrafficDistribution()).isEqualTo("PreferClose");
        assertThat(spec.getType()).isEqualTo("LoadBalancer");
        ModelJson.assertJsonEquals(ModelJson.SERVICE_SPEC, spec.toJson());
    }
}
