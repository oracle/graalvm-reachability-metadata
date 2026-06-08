/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_vertx.vertx_core;

import io.vertx.core.Completable;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VertxBuilderTest {

    @Test
    void buildClusteredUsesConfiguredClusterManager() throws Exception {
        int instancesBeforeInit = SystemPropertyClusterManager.instances();
        SystemPropertyClusterManager clusterManager = new SystemPropertyClusterManager();
        Vertx vertx = null;
        try {
            vertx = Vertx.builder()
                    .withClusterManager(clusterManager)
                    .buildClustered()
                    .toCompletionStage()
                    .toCompletableFuture()
                    .get(10, TimeUnit.SECONDS);

            assertNotNull(vertx);
            assertTrue(clusterManager.initialized());
            assertTrue(clusterManager.joined());
            assertEquals(instancesBeforeInit + 1, SystemPropertyClusterManager.instances());
        } finally {
            if (vertx != null) {
                vertx.close().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
            }
        }
    }

    public static class SystemPropertyClusterManager implements ClusterManager {
        private static final AtomicInteger INSTANCES = new AtomicInteger();

        private boolean initialized;
        private boolean joined;

        public SystemPropertyClusterManager() {
            INSTANCES.incrementAndGet();
        }

        static int instances() {
            return INSTANCES.get();
        }

        boolean initialized() {
            return initialized;
        }

        boolean joined() {
            return joined;
        }

        @Override
        public void init(Vertx vertx) {
            initialized = true;
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
            joined = true;
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
