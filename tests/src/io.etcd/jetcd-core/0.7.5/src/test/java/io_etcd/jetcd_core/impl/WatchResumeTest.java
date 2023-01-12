/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_etcd.jetcd_core.impl;

import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.KV;
import io.etcd.jetcd.Watch;
import io.etcd.jetcd.Watch.Watcher;
import io.etcd.jetcd.test.EtcdClusterExtension;
import io.etcd.jetcd.watch.WatchEvent.EventType;
import io.etcd.jetcd.watch.WatchResponse;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

// `@org.junit.jupiter.api.Timeout(value = 30)` can't be used in the nativeTest GraalVM CE 22.3
public class WatchResumeTest {
    @RegisterExtension
    public static final EtcdClusterExtension cluster = EtcdClusterExtension.builder()
            .withNodes(3)
            .build();

    @Test
    @Disabled("https://github.com/etcd-io/jetcd/pull/1092")
    public void testWatchOnPut() throws Exception {
        try (Client client = TestUtil.client(cluster).build()) {
            Watch watchClient = client.getWatchClient();
            KV kvClient = client.getKVClient();
            final ByteSequence key = TestUtil.randomByteSequence();
            final ByteSequence value = TestUtil.randomByteSequence();
            final AtomicReference<WatchResponse> ref = new AtomicReference<>();
            try (Watcher ignored = watchClient.watch(key, ref::set)) {
                cluster.restart();
                kvClient.put(key, value).get(1, TimeUnit.SECONDS);
                await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> assertThat(ref.get()).isNotNull());
                assertThat(ref.get().getEvents().size()).isEqualTo(1);
                assertThat(ref.get().getEvents().get(0).getEventType()).isEqualTo(EventType.PUT);
                assertThat(ref.get().getEvents().get(0).getKeyValue().getKey()).isEqualTo(key);
            }
        }
    }
}
