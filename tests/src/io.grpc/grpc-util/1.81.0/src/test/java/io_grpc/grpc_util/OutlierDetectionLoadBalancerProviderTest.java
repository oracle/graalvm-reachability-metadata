/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import io.grpc.ManagedChannel;
import io.grpc.NameResolver.ConfigOrError;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.util.GracefulSwitchLoadBalancer;

public class OutlierDetectionLoadBalancerProviderTest {
    private static final String POLICY = "outlier_detection_experimental";
    private static final List<Map<String, ?>> CHILD_POLICY = List.of(Map.of("round_robin", Map.of()));

    @Test
    void serviceConfigTracksSuccessfulRequestsAndDelegatesToRoundRobin() throws Exception {
        Map<String, ?> config = Map.of(
                "interval", "60s",
                "baseEjectionTime", "30s",
                "maxEjectionTime", "120s",
                "maxEjectionPercentage", 50.0,
                "successRateEjection", Map.of("minimumHosts", 2.0, "requestVolume", 5.0),
                "failurePercentageEjection", Map.of("threshold", 50.0, "minimumHosts", 2.0, "requestVolume", 5.0),
                "childPolicy", CHILD_POLICY);
        ConfigOrError parsed = GracefulSwitchLoadBalancer.parseLoadBalancingPolicyConfig(
                List.of(Map.of(POLICY, config)));
        assertThat(parsed.getError()).isNull();
        assertThat(parsed.getConfig()).isNotNull();

        String left = InProcessServerBuilder.generateName();
        String right = InProcessServerBuilder.generateName();
        try (RpcTestSupport rpc = new RpcTestSupport()) {
            rpc.startServer(InProcessServerBuilder.forName(left).addService(RpcTestSupport.echoService("left:")));
            rpc.startServer(InProcessServerBuilder.forName(right).addService(RpcTestSupport.echoService("right:")));
            rpc.registerResolver(RpcTestSupport.resolverFor(List.of(left, right)));
            ManagedChannel channel = rpc.openChannel(InProcessChannelBuilder.forTarget("utility-test:///outliers")
                    .disableServiceConfigLookUp()
                    .defaultServiceConfig(Map.of("loadBalancingConfig", List.of(Map.of(POLICY, config)))));

            Set<String> readyServers = new HashSet<>();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            while (readyServers.size() < 2 && System.nanoTime() < deadline) {
                readyServers.add(RpcTestSupport.call(channel, "warmup"));
                if (readyServers.size() < 2) {
                    Thread.sleep(10);
                }
            }
            assertThat(readyServers).containsExactlyInAnyOrder("left:warmup", "right:warmup");
            List<String> responses = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                responses.add(RpcTestSupport.call(channel, "payload"));
            }
            assertThat(responses).containsOnly("left:payload", "right:payload");
            assertThat(responses.stream().filter("left:payload"::equals).count()).isEqualTo(5);
            assertThat(responses.stream().filter("right:payload"::equals).count()).isEqualTo(5);
        }
    }

    @Test
    void rejectsUnavailableChildrenAndInvalidEjectionPercentages() {
        List<Map<String, ?>> invalidConfigs = List.of(
                Map.of("childPolicy", List.of(Map.of("unknown_test_policy", Map.of()))),
                Map.of("childPolicy", CHILD_POLICY,
                        "successRateEjection", Map.of("enforcementPercentage", 101.0)),
                Map.of("childPolicy", CHILD_POLICY,
                        "failurePercentageEjection", Map.of("threshold", -1.0)));
        for (Map<String, ?> config : invalidConfigs) {
            ConfigOrError parsed = GracefulSwitchLoadBalancer.parseLoadBalancingPolicyConfig(
                    List.of(Map.of(POLICY, config)));
            assertThat(parsed.getConfig()).isNull();
            assertThat(parsed.getError()).isNotNull();
            assertThat(parsed.getError().getCode()).isEqualTo(Status.Code.UNAVAILABLE);
        }
    }
}
