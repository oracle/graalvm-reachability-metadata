/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_vertx.vertx_core;

import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.impl.VertxBuilder;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.core.shareddata.Counter;
import io.vertx.core.shareddata.Lock;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.core.spi.cluster.NodeInfo;
import io.vertx.core.spi.cluster.NodeListener;
import io.vertx.core.spi.cluster.NodeSelector;
import io.vertx.core.spi.cluster.RegistrationInfo;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class VertxBuilderTest {

    private static final String CLUSTER_MANAGER_CLASS_PROPERTY = "vertx.cluster.managerClass";

    @Test
    void initInstantiatesClusterManagerFromSystemProperty() {
        int instancesBeforeInit = SystemPropertyClusterManager.instances();
        String previousClusterManagerClass = System.getProperty(CLUSTER_MANAGER_CLASS_PROPERTY);
        System.setProperty(CLUSTER_MANAGER_CLASS_PROPERTY, SystemPropertyClusterManager.class.getName());
        try {
            VertxBuilder builder = new VertxBuilder();

            builder.init();

            assertInstanceOf(SystemPropertyClusterManager.class, builder.clusterManager());
            assertEquals(instancesBeforeInit + 1, SystemPropertyClusterManager.instances());
        } finally {
            restoreSystemProperty(previousClusterManagerClass);
        }
    }

    private static void restoreSystemProperty(String previousValue) {
        if (previousValue == null) {
            System.clearProperty(CLUSTER_MANAGER_CLASS_PROPERTY);
        } else {
            System.setProperty(CLUSTER_MANAGER_CLASS_PROPERTY, previousValue);
        }
    }

    public static class SystemPropertyClusterManager implements ClusterManager {
        private static final AtomicInteger INSTANCES = new AtomicInteger();

        public SystemPropertyClusterManager() {
            INSTANCES.incrementAndGet();
        }

        static int instances() {
            return INSTANCES.get();
        }

        @Override
        public void init(Vertx vertx, NodeSelector nodeSelector) {
        }

        @Override
        public <K, V> void getAsyncMap(String name, Promise<AsyncMap<K, V>> promise) {
            promise.fail(new UnsupportedOperationException("Async maps are not used by this test cluster manager"));
        }

        @Override
        public <K, V> Map<K, V> getSyncMap(String name) {
            return Collections.emptyMap();
        }

        @Override
        public void getLockWithTimeout(String name, long timeout, Promise<Lock> promise) {
            promise.fail(new UnsupportedOperationException("Locks are not used by this test cluster manager"));
        }

        @Override
        public void getCounter(String name, Promise<Counter> promise) {
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
        public void setNodeInfo(NodeInfo nodeInfo, Promise<Void> promise) {
            promise.complete();
        }

        @Override
        public NodeInfo getNodeInfo() {
            return null;
        }

        @Override
        public void getNodeInfo(String nodeId, Promise<NodeInfo> promise) {
            promise.complete(null);
        }

        @Override
        public void join(Promise<Void> promise) {
            promise.complete();
        }

        @Override
        public void leave(Promise<Void> promise) {
            promise.complete();
        }

        @Override
        public boolean isActive() {
            return true;
        }

        @Override
        public void addRegistration(String address, RegistrationInfo registrationInfo, Promise<Void> promise) {
            promise.complete();
        }

        @Override
        public void removeRegistration(String address, RegistrationInfo registrationInfo, Promise<Void> promise) {
            promise.complete();
        }

        @Override
        public void getRegistrations(String address, Promise<List<RegistrationInfo>> promise) {
            promise.complete(Collections.emptyList());
        }
    }
}
