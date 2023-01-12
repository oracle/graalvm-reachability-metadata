/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_etcd.jetcd_core.impl;

import io.etcd.jetcd.Client;
import io.etcd.jetcd.ClientBuilder;
import io.etcd.jetcd.KV;
import io.etcd.jetcd.Response;
import io.etcd.jetcd.kv.PutResponse;
import io.etcd.jetcd.test.EtcdClusterExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

// `@org.junit.jupiter.api.Timeout(value = 30)` can't be used in the nativeTest GraalVM CE 22.3
public class LoadBalancerTest {
    @RegisterExtension
    public static final EtcdClusterExtension cluster = EtcdClusterExtension.builder()
            .withNodes(3)
            .build();

    @Test
    public void testPickFirstBalancerFactory() throws Exception {
        final List<URI> endpoints = cluster.clientEndpoints();
        final ClientBuilder builder = Client.builder().endpoints(endpoints).loadBalancerPolicy("pick_first");
        try (Client client = builder.build(); KV kv = client.getKVClient()) {
            long lastMemberId = 0;
            final String allEndpoints = endpoints.stream().map(URI::toString).collect(Collectors.joining(","));
            for (int i = 0; i < allEndpoints.length() * 2; i++) {
                Response response = kv.put(TestUtil.randomByteSequence(), TestUtil.randomByteSequence()).get();
                if (i == 0) {
                    lastMemberId = response.getHeader().getMemberId();
                }
                assertThat(response.getHeader().getMemberId()).isEqualTo(lastMemberId);
            }
        }
    }

    @Test
    public void testRoundRobinLoadBalancerFactory() throws Exception {
        final List<URI> endpoints = cluster.clientEndpoints();
        final ClientBuilder builder = Client.builder().endpoints(endpoints).loadBalancerPolicy("round_robin");
        try (Client client = builder.build(); KV kv = client.getKVClient()) {
            long lastMemberId = 0;
            long differences = 0;
            final String allEndpoints = endpoints.stream().map(URI::toString).collect(Collectors.joining(","));
            for (int i = 0; i < allEndpoints.length(); i++) {
                PutResponse response = kv.put(TestUtil.randomByteSequence(), TestUtil.randomByteSequence()).get();
                if (i > 0 && lastMemberId != response.getHeader().getMemberId()) {
                    differences++;
                }
                lastMemberId = response.getHeader().getMemberId();
            }
            assertThat(differences).isNotEqualTo(lastMemberId);
        }
    }
}
