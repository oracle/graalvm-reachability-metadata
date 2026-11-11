/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.grpc.ManagedChannel;
import io.grpc.NameResolver.ConfigOrError;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.util.GracefulSwitchLoadBalancer;

public class RandomSubsettingLoadBalancerProviderTest {
    private static final String POLICY = "random_subsetting_experimental";
    private static final List<Map<String, ?>> CHILD_POLICY = List.of(Map.of("round_robin", Map.of()));

    @Test
    void serviceConfigRoutesRequestsToOneStableSubsetMember() throws Exception {
        Map<String, ?> policyConfig = Map.of("subsetSize", 1.0, "childPolicy", CHILD_POLICY);
        ConfigOrError parsed = GracefulSwitchLoadBalancer.parseLoadBalancingPolicyConfig(
                List.of(Map.of(POLICY, policyConfig)));
        assertThat(parsed.getError()).isNull();
        assertThat(parsed.getConfig()).isNotNull();

        List<String> names = List.of(InProcessServerBuilder.generateName(),
                InProcessServerBuilder.generateName(), InProcessServerBuilder.generateName());
        try (RpcTestSupport rpc = new RpcTestSupport()) {
            for (int i = 0; i < names.size(); i++) {
                rpc.startServer(InProcessServerBuilder.forName(names.get(i))
                        .addService(RpcTestSupport.echoService("backend-" + i + ":")));
            }
            rpc.registerResolver(RpcTestSupport.resolverFor(names));
            ManagedChannel channel = rpc.openChannel(InProcessChannelBuilder.forTarget("utility-test:///subset")
                    .disableServiceConfigLookUp()
                    .defaultServiceConfig(Map.of("loadBalancingConfig", List.of(Map.of(POLICY, policyConfig)))));

            String selected = RpcTestSupport.call(channel, "payload");
            assertThat(selected).isIn("backend-0:payload", "backend-1:payload", "backend-2:payload");
            for (int i = 0; i < 12; i++) {
                assertThat(RpcTestSupport.call(channel, "payload")).isEqualTo(selected);
            }
        }
    }

    @Test
    void rejectsMissingOrNonPositiveSubsetSizesAndUnavailableChildPolicies() {
        List<Map<String, ?>> invalidConfigs = List.of(
                Map.of("childPolicy", CHILD_POLICY),
                Map.of("subsetSize", 0.0, "childPolicy", CHILD_POLICY),
                Map.of("subsetSize", -1.0, "childPolicy", CHILD_POLICY),
                Map.of("subsetSize", 1.0, "childPolicy", List.of(Map.of("unknown_test_policy", Map.of()))));
        for (Map<String, ?> config : invalidConfigs) {
            ConfigOrError parsed = GracefulSwitchLoadBalancer.parseLoadBalancingPolicyConfig(
                    List.of(Map.of(POLICY, config)));
            assertThat(parsed.getConfig()).isNull();
            assertThat(parsed.getError()).isNotNull();
            assertThat(parsed.getError().getCode()).isEqualTo(Status.Code.UNAVAILABLE);
        }
    }
}
