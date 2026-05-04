/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_services;

import io.grpc.protobuf.services.internal.HealthCheckingRoundRobinLoadBalancerProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class HealthCheckingRoundRobinLoadBalancerProviderTest {
    @Test
    void constructsReflectiveRoundRobinProvider() {
        HealthCheckingRoundRobinLoadBalancerProvider provider =
                new HealthCheckingRoundRobinLoadBalancerProvider();

        assertThat(provider.isAvailable()).isTrue();
        assertThat(provider.getPolicyName()).isEqualTo("round_robin");
        assertThat(provider.getPriority()).isPositive();
    }
}
