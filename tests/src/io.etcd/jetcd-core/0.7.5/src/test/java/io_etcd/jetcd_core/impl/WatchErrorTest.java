/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_etcd.jetcd_core.impl;

import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.Watch.Watcher;
import io.etcd.jetcd.common.exception.EtcdException;
import io.etcd.jetcd.test.EtcdClusterExtension;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static io_etcd.jetcd_core.impl.TestUtil.bytesOf;
import static io_etcd.jetcd_core.impl.TestUtil.randomByteSequence;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SuppressWarnings({"resource", "JUnitMalformedDeclaration"})
// `@org.junit.jupiter.api.Timeout(value = 30)` can't be used in the nativeTest GraalVM CE 22.3
public class WatchErrorTest {
    @RegisterExtension
    public final EtcdClusterExtension cluster = EtcdClusterExtension.builder()
        .withNodes(3)
        .build();

    @ParameterizedTest
    @ValueSource(strings = { "test-namespace/", "" })
    public void testWatchOnError(String ns) {
        final Client client = ns != null && ns.length() == 0
            ? TestUtil.client(cluster).namespace(bytesOf(ns)).build()
            : TestUtil.client(cluster).build();
        final ByteSequence key = randomByteSequence();
        final List<Throwable> events = Collections.synchronizedList(new ArrayList<>());
        try (Watcher ignored = client.getWatchClient().watch(key, TestUtil::noOpWatchResponseConsumer, events::add)) {
            cluster.cluster().stop();
            await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> assertThat(events).isNotEmpty());
        }
        assertThat(events).allMatch(EtcdException.class::isInstance);
    }
}
