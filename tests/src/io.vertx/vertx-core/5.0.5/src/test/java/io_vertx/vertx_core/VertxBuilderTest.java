/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_vertx.vertx_core;

import io.vertx.core.AsyncResult;
import io.vertx.core.Completable;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.core.shareddata.Counter;
import io.vertx.core.shareddata.Lock;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.core.spi.cluster.NodeInfo;
import io.vertx.core.spi.cluster.NodeListener;
import io.vertx.core.spi.cluster.RegistrationInfo;
import io.vertx.core.spi.cluster.RegistrationListener;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VertxBuilderTest {

    @Test
    void buildClusteredUsesConfiguredClusterManager() throws Exception {
        int instancesBeforeInit = SystemPropertyClusterManager.instances();
        SystemPropertyClusterManager clusterManager = new SystemPropertyClusterManager();
        Vertx vertx = null;
        try {
            vertx = await(Vertx.builder().withClusterManager(clusterManager).buildClustered());

            assertNotNull(vertx);
            assertSame(vertx, clusterManager.initializedVertx());
            assertEquals(instancesBeforeInit + 1, SystemPropertyClusterManager.instances());
        } finally {
            closeVertx(vertx);
        }
    }

    private static <T> T await(Future<T> future) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<AsyncResult<T>> result = new AtomicReference<>();

        future.onComplete(completion -> {
            result.set(completion);
            latch.countDown();
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertNotNull(result.get());
        assertTrue(result.get().succeeded(), () -> String.valueOf(result.get().cause()));
        return result.get().result();
    }

    private static void closeVertx(Vertx vertx) throws InterruptedException {
        if (vertx != null) {
            await(vertx.close());
        }
    }

    public static class SystemPropertyClusterManager implements ClusterManager {
        private static final AtomicInteger INSTANCES = new AtomicInteger();
        private Vertx initializedVertx;

        public SystemPropertyClusterManager() {
            INSTANCES.incrementAndGet();
        }

        static int instances() {
            return INSTANCES.get();
        }

        Vertx initializedVertx() {
            return initializedVertx;
        }

        @Override
        public void init(Vertx vertx) {
            initializedVertx = vertx;
        }

        @Override
        public <K, V> void getAsyncMap(String name, Completable<AsyncMap<K, V>> promise) {
            promise.fail(new UnsupportedOperationException("Async maps are not used by this test cluster manager"));
        }

        @Override
        public <K, V> Map<K, V> getSyncMap(String name) {
            return Collections.emptyMap();
        }

        @Override
        public void getLockWithTimeout(String name, long timeout, Completable<Lock> promise) {
            promise.fail(new UnsupportedOperationException("Locks are not used by this test cluster manager"));
        }

        @Override
        public void getCounter(String name, Completable<Counter> promise) {
            promise.fail(new UnsupportedOperationException("Counters are not used by this test cluster manager"));
        }

        @Override
        public String getNodeId() {
            return "test-node";
        }

        @Override
        public List<String> getNodes() {
            return Collections.singletonList(getNodeId());
        }

        @Override
        public void nodeListener(NodeListener listener) {
        }

        @Override
        public void setNodeInfo(NodeInfo nodeInfo, Completable<Void> promise) {
            promise.succeed();
        }

        @Override
        public NodeInfo getNodeInfo() {
            return null;
        }

        @Override
        public void getNodeInfo(String nodeId, Completable<NodeInfo> promise) {
            promise.succeed(null);
        }

        @Override
        public void join(Completable<Void> promise) {
            promise.succeed();
        }

        @Override
        public void leave(Completable<Void> promise) {
            promise.succeed();
        }

        @Override
        public boolean isActive() {
            return true;
        }

        @Override
        public void registrationListener(RegistrationListener registrationListener) {
        }

        @Override
        public void addRegistration(String address, RegistrationInfo registrationInfo, Completable<Void> promise) {
            promise.succeed();
        }

        @Override
        public void removeRegistration(String address, RegistrationInfo registrationInfo, Completable<Void> promise) {
            promise.succeed();
        }

        @Override
        public void getRegistrations(String address, Completable<List<RegistrationInfo>> promise) {
            promise.succeed(Collections.emptyList());
        }
    }
}
