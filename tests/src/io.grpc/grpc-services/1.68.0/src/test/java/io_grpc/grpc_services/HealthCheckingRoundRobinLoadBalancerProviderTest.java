/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_services;

import io.grpc.LoadBalancerProvider;
import io.grpc.LoadBalancerRegistry;
import io.grpc.NameResolver.ConfigOrError;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class HealthCheckingRoundRobinLoadBalancerProviderTest {
    @Test
    void loadBalancerRegistryProvidesHealthCheckingRoundRobinPolicy() {
        LoadBalancerProvider provider = LoadBalancerRegistry.getDefaultRegistry().getProvider("round_robin");

        assertThat(provider).isNotNull();
        assertThat(provider.getClass().getName())
                .isEqualTo("io.grpc.protobuf.services.internal.HealthCheckingRoundRobinLoadBalancerProvider");
        assertThat(provider.isAvailable()).isTrue();
        assertThat(provider.getPolicyName()).isEqualTo("round_robin");
        assertThat(provider.getPriority()).isPositive();

        ConfigOrError parsed = provider.parseLoadBalancingPolicyConfig(Map.of());
        assertThat(parsed.getError()).isNull();
        assertThat(parsed.getConfig()).isNotNull();
    }
}
