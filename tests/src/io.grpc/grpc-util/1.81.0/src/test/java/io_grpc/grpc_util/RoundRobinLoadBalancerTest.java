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
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import io.grpc.ManagedChannel;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;

public class RoundRobinLoadBalancerTest {
    @Test
    void distributesRequestsAcrossReadyServers() throws Exception {
        String left = InProcessServerBuilder.generateName();
        String right = InProcessServerBuilder.generateName();

        try (RpcTestSupport rpc = new RpcTestSupport()) {
            rpc.startServer(InProcessServerBuilder.forName(left)
                    .addService(RpcTestSupport.echoService("left:")));
            rpc.startServer(InProcessServerBuilder.forName(right)
                    .addService(RpcTestSupport.echoService("right:")));
            rpc.registerResolver(RpcTestSupport.resolverFor(List.of(left, right)));
            ManagedChannel channel = rpc.openChannel(InProcessChannelBuilder.forTarget("utility-test:///backends")
                    .defaultLoadBalancingPolicy("round_robin"));

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
            for (int i = 0; i < 8; i++) {
                responses.add(RpcTestSupport.call(channel, "payload"));
            }
            assertThat(responses).containsOnly("left:payload", "right:payload");
            assertThat(responses.stream().filter("left:payload"::equals).count()).isEqualTo(4);
            assertThat(responses.stream().filter("right:payload"::equals).count()).isEqualTo(4);
            for (int i = 1; i < responses.size(); i++) {
                assertThat(responses.get(i)).isNotEqualTo(responses.get(i - 1));
            }
        }
    }
}
