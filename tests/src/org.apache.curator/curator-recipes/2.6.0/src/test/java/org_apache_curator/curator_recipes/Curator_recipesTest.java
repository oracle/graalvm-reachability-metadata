/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_curator.curator_recipes;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.atomic.AtomicValue;
import org.apache.curator.framework.recipes.atomic.CachedAtomicInteger;
import org.apache.curator.framework.recipes.atomic.DistributedAtomicInteger;
import org.apache.curator.framework.recipes.atomic.DistributedAtomicLong;
import org.apache.curator.framework.recipes.barriers.DistributedBarrier;
import org.apache.curator.framework.recipes.barriers.DistributedDoubleBarrier;
import org.apache.curator.framework.recipes.cache.ChildData;
import org.apache.curator.framework.recipes.cache.NodeCache;
import org.apache.curator.framework.recipes.cache.PathChildrenCache;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent;
import org.apache.curator.framework.recipes.leader.LeaderLatch;
import org.apache.curator.framework.recipes.leader.Participant;
import org.apache.curator.framework.recipes.locks.ChildReaper;
import org.apache.curator.framework.recipes.locks.InterProcessLock;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.apache.curator.framework.recipes.locks.InterProcessReadWriteLock;
import org.apache.curator.framework.recipes.locks.InterProcessSemaphoreV2;
import org.apache.curator.framework.recipes.locks.Lease;
import org.apache.curator.framework.recipes.locks.Reaper;
import org.apache.curator.framework.recipes.nodes.PersistentEphemeralNode;
import org.apache.curator.framework.recipes.queue.SimpleDistributedQueue;
import org.apache.curator.framework.recipes.shared.SharedCount;
import org.apache.curator.framework.recipes.shared.SharedCountListener;
import org.apache.curator.framework.recipes.shared.SharedCountReader;
import org.apache.curator.framework.recipes.shared.SharedValue;
import org.apache.curator.framework.recipes.shared.SharedValueListener;
import org.apache.curator.framework.recipes.shared.SharedValueReader;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.curator.retry.RetryOneTime;
import org.apache.curator.utils.CloseableUtils;
import org.apache.zookeeper.server.NIOServerCnxnFactory;
import org.apache.zookeeper.server.ZooKeeperServer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Curator_recipesTest {
    private static final int SESSION_TIMEOUT_MS = 5_000;
    private static final int CONNECTION_TIMEOUT_MS = 3_000;

    @Test
    void distributedAtomicNumbersCoordinateCompareAndCachedOperations() throws Exception {
        try (LocalZooKeeperServer server = LocalZooKeeperServer.start();
                CuratorFramework client = newClient(server)) {
            client.create().creatingParentsIfNeeded().forPath("/atomic");

            DistributedAtomicInteger counter = new DistributedAtomicInteger(
                    client, "/atomic/counter", new RetryOneTime(25));
            assertThat(counter.initialize(10)).isTrue();
            assertThat(counter.initialize(99)).isFalse();

            AtomicValue<Integer> increment = counter.increment();
            assertThat(increment.succeeded()).isTrue();
            assertThat(increment.preValue()).isEqualTo(10);
            assertThat(increment.postValue()).isEqualTo(11);

            AtomicValue<Integer> failedCompareAndSet = counter.compareAndSet(10, 20);
            assertThat(failedCompareAndSet.succeeded()).isFalse();
            assertThat(failedCompareAndSet.preValue()).isEqualTo(11);
            assertThat(counter.get().postValue()).isEqualTo(11);

            AtomicValue<Integer> successfulCompareAndSet = counter.compareAndSet(11, 20);
            assertThat(successfulCompareAndSet.succeeded()).isTrue();
            assertThat(successfulCompareAndSet.preValue()).isEqualTo(11);
            assertThat(successfulCompareAndSet.postValue()).isEqualTo(20);

            CachedAtomicInteger cachedCounter = new CachedAtomicInteger(counter, 3);
            assertThat(cachedCounter.next().postValue()).isEqualTo(21);
            assertThat(cachedCounter.next().postValue()).isEqualTo(22);
            assertThat(cachedCounter.next().postValue()).isEqualTo(23);
            assertThat(counter.get().postValue()).isEqualTo(23);

            DistributedAtomicLong total = new DistributedAtomicLong(client, "/atomic/total", new RetryOneTime(25));
            assertThat(total.initialize(100L)).isTrue();
            AtomicValue<Long> add = total.add(25L);
            assertThat(add.succeeded()).isTrue();
            assertThat(add.preValue()).isEqualTo(100L);
            assertThat(add.postValue()).isEqualTo(125L);
            assertThat(total.subtract(5L).postValue()).isEqualTo(120L);
        }
    }

    @Test
    void sharedValuesCachesAndPersistentNodesPublishDataChanges() throws Exception {
        try (LocalZooKeeperServer server = LocalZooKeeperServer.start();
                CuratorFramework client = newClient(server)) {
            client.create().creatingParentsIfNeeded().forPath("/cache/node", bytes("initial"));
            NodeCache nodeCache = new NodeCache(client, "/cache/node");
            PathChildrenCache childrenCache = new PathChildrenCache(client, "/cache/children", true);
            SharedValue sharedValue = new SharedValue(client, "/shared/value", bytes("one"));
            SharedCount sharedCount = new SharedCount(client, "/shared/count", 1);
            PersistentEphemeralNode ephemeralNode = new PersistentEphemeralNode(
                    client, PersistentEphemeralNode.Mode.EPHEMERAL, "/ephemeral/live", bytes("alive"));

            try {
                CountDownLatch nodeChanged = new CountDownLatch(1);
                nodeCache.getListenable().addListener(() -> {
                    ChildData currentData = nodeCache.getCurrentData();
                    if (currentData != null && "updated".equals(string(currentData.getData()))) {
                        nodeChanged.countDown();
                    }
                });
                nodeCache.start(true);
                assertThat(string(nodeCache.getCurrentData().getData())).isEqualTo("initial");

                CountDownLatch childAdded = new CountDownLatch(1);
                CountDownLatch childUpdated = new CountDownLatch(1);
                CountDownLatch childRemoved = new CountDownLatch(1);
                childrenCache.getListenable().addListener((curator, event) -> {
                    if (event.getType() == PathChildrenCacheEvent.Type.CHILD_ADDED) {
                        childAdded.countDown();
                    } else if (event.getType() == PathChildrenCacheEvent.Type.CHILD_UPDATED) {
                        childUpdated.countDown();
                    } else if (event.getType() == PathChildrenCacheEvent.Type.CHILD_REMOVED) {
                        childRemoved.countDown();
                    }
                });
                childrenCache.start(PathChildrenCache.StartMode.BUILD_INITIAL_CACHE);

                AtomicReference<String> sharedValueUpdate = new AtomicReference<>();
                CountDownLatch sharedValueChanged = new CountDownLatch(1);
                sharedValue.getListenable().addListener(new SharedValueListener() {
                    @Override
                    public void valueHasChanged(SharedValueReader sharedValueReader, byte[] newValue) {
                        sharedValueUpdate.set(string(newValue));
                        sharedValueChanged.countDown();
                    }

                    @Override
                    public void stateChanged(CuratorFramework curator, ConnectionState newState) {
                    }
                });
                sharedValue.start();

                AtomicInteger sharedCountUpdate = new AtomicInteger();
                CountDownLatch sharedCountChanged = new CountDownLatch(1);
                sharedCount.addListener(new SharedCountListener() {
                    @Override
                    public void countHasChanged(SharedCountReader sharedCountReader, int newCount) {
                        sharedCountUpdate.set(newCount);
                        sharedCountChanged.countDown();
                    }

                    @Override
                    public void stateChanged(CuratorFramework curator, ConnectionState newState) {
                    }
                });
                sharedCount.start();

                ephemeralNode.start();
                assertThat(ephemeralNode.waitForInitialCreate(5, TimeUnit.SECONDS)).isTrue();
                assertThat(string(client.getData().forPath(ephemeralNode.getActualPath()))).isEqualTo("alive");

                client.setData().forPath("/cache/node", bytes("updated"));
                assertThat(nodeChanged.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(string(nodeCache.getCurrentData().getData())).isEqualTo("updated");

                client.create().creatingParentsIfNeeded().forPath("/cache/children/first", bytes("child-one"));
                assertThat(childAdded.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(string(childrenCache.getCurrentData("/cache/children/first").getData()))
                        .isEqualTo("child-one");

                client.setData().forPath("/cache/children/first", bytes("child-two"));
                assertThat(childUpdated.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(string(childrenCache.getCurrentData("/cache/children/first").getData()))
                        .isEqualTo("child-two");

                client.delete().forPath("/cache/children/first");
                assertThat(childRemoved.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(childrenCache.getCurrentData("/cache/children/first")).isNull();

                sharedValue.setValue(bytes("two"));
                assertThat(sharedValueChanged.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(sharedValueUpdate).hasValue("two");
                assertThat(string(sharedValue.getValue())).isEqualTo("two");

                assertThat(sharedCount.trySetCount(7)).isTrue();
                assertThat(sharedCountChanged.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(sharedCountUpdate).hasValue(7);
                assertThat(sharedCount.getCount()).isEqualTo(7);

                ephemeralNode.setData(bytes("still-alive"));
                assertThat(string(client.getData().forPath(ephemeralNode.getActualPath()))).isEqualTo("still-alive");
                String ephemeralPath = ephemeralNode.getActualPath();
                ephemeralNode.close();
                awaitUntil(() -> {
                    try {
                        return client.checkExists().forPath(ephemeralPath) == null;
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                });
            } finally {
                closeQuietly(ephemeralNode);
                closeQuietly(sharedCount);
                closeQuietly(sharedValue);
                closeQuietly(childrenCache);
                closeQuietly(nodeCache);
            }
        }
    }

    @Test
    void locksSemaphoresAndLeaderLatchCoordinateMultipleClients() throws Exception {
        try (LocalZooKeeperServer server = LocalZooKeeperServer.start();
                CuratorFramework firstClient = newClient(server);
                CuratorFramework secondClient = newClient(server)) {
            InterProcessMutex firstMutex = new InterProcessMutex(firstClient, "/locks/mutex");
            InterProcessMutex secondMutex = new InterProcessMutex(secondClient, "/locks/mutex");

            assertThat(firstMutex.acquire(5, TimeUnit.SECONDS)).isTrue();
            assertThat(firstMutex.isAcquiredInThisProcess()).isTrue();
            assertThat(firstMutex.acquire(5, TimeUnit.SECONDS)).isTrue();
            assertThat(secondMutex.acquire(100, TimeUnit.MILLISECONDS)).isFalse();
            firstMutex.release();
            assertThat(secondMutex.acquire(100, TimeUnit.MILLISECONDS)).isFalse();
            firstMutex.release();
            assertThat(secondMutex.acquire(5, TimeUnit.SECONDS)).isTrue();
            assertThat(secondMutex.getParticipantNodes()).hasSize(1);
            secondMutex.release();

            InterProcessSemaphoreV2 semaphore = new InterProcessSemaphoreV2(firstClient, "/locks/semaphore", 1);
            semaphore.setNodeData(bytes("lease-data"));
            Lease firstLease = semaphore.acquire(5, TimeUnit.SECONDS);
            assertThat(firstLease).isNotNull();
            assertThat(string(firstLease.getData())).isEqualTo("lease-data");
            assertThat(semaphore.acquire(100, TimeUnit.MILLISECONDS)).isNull();
            firstLease.close();
            Lease secondLease = semaphore.acquire(5, TimeUnit.SECONDS);
            assertThat(secondLease).isNotNull();
            semaphore.returnLease(secondLease);

            LeaderLatch firstLatch = new LeaderLatch(firstClient, "/leader/latch", "first");
            LeaderLatch secondLatch = new LeaderLatch(secondClient, "/leader/latch", "second");
            try {
                firstLatch.start();
                assertThat(firstLatch.await(5, TimeUnit.SECONDS)).isTrue();
                secondLatch.start();
                assertThat(firstLatch.hasLeadership()).isTrue();
                assertThat(secondLatch.await(100, TimeUnit.MILLISECONDS)).isFalse();

                Participant leader = firstLatch.getLeader();
                assertThat(leader.getId()).isEqualTo("first");
                assertThat(leader.isLeader()).isTrue();
                Collection<Participant> participants = firstLatch.getParticipants();
                assertThat(participants).extracting(Participant::getId).contains("first", "second");

                firstLatch.close();
                assertThat(secondLatch.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(secondLatch.hasLeadership()).isTrue();
                assertThat(secondLatch.getLeader().getId()).isEqualTo("second");
            } finally {
                closeLeaderLatchIfStarted(firstLatch);
                closeLeaderLatchIfStarted(secondLatch);
            }
        }
    }

    @Test
    void interProcessReadWriteLockCoordinatesSharedReadersAndExclusiveWriters() throws Exception {
        try (LocalZooKeeperServer server = LocalZooKeeperServer.start();
                CuratorFramework firstClient = newClient(server);
                CuratorFramework secondClient = newClient(server)) {
            InterProcessReadWriteLock firstLocks = new InterProcessReadWriteLock(firstClient, "/locks/read-write");
            InterProcessReadWriteLock secondLocks = new InterProcessReadWriteLock(secondClient, "/locks/read-write");
            InterProcessLock firstReadLock = firstLocks.readLock();
            InterProcessLock secondReadLock = secondLocks.readLock();
            InterProcessLock firstWriteLock = firstLocks.writeLock();
            InterProcessLock secondWriteLock = secondLocks.writeLock();

            assertThat(firstReadLock.acquire(5, TimeUnit.SECONDS)).isTrue();
            try {
                assertThat(secondReadLock.acquire(5, TimeUnit.SECONDS)).isTrue();
                try {
                    assertThat(secondWriteLock.acquire(100, TimeUnit.MILLISECONDS)).isFalse();
                } finally {
                    secondReadLock.release();
                }
            } finally {
                firstReadLock.release();
            }

            assertThat(secondWriteLock.acquire(5, TimeUnit.SECONDS)).isTrue();
            try {
                assertThat(firstReadLock.acquire(100, TimeUnit.MILLISECONDS)).isFalse();
                assertThat(firstWriteLock.acquire(100, TimeUnit.MILLISECONDS)).isFalse();
            } finally {
                secondWriteLock.release();
            }

            assertThat(firstWriteLock.acquire(5, TimeUnit.SECONDS)).isTrue();
            firstWriteLock.release();
        }
    }

    @Test
    void childReaperRemovesEmptyChildrenAndRetainsNonEmptyChildren() throws Exception {
        try (LocalZooKeeperServer server = LocalZooKeeperServer.start();
                CuratorFramework client = newClient(server);
                ChildReaper childReaper = new ChildReaper(
                        client, "/reaper/root", Reaper.Mode.REAP_UNTIL_GONE, 300)) {
            client.create().creatingParentsIfNeeded().forPath("/reaper/root/empty", bytes("empty"));
            client.create().creatingParentsIfNeeded().forPath("/reaper/root/non-empty/child", bytes("child"));

            childReaper.start();

            awaitUntil(() -> {
                try {
                    return client.checkExists().forPath("/reaper/root/empty") == null;
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
            assertThat(client.checkExists().forPath("/reaper/root/non-empty")).isNotNull();
            assertThat(string(client.getData().forPath("/reaper/root/non-empty/child"))).isEqualTo("child");
        }
    }

    @Test
    void queuesAndBarriersSynchronizeParticipants() throws Exception {
        try (LocalZooKeeperServer server = LocalZooKeeperServer.start();
                CuratorFramework firstClient = newClient(server);
                CuratorFramework secondClient = newClient(server)) {
            SimpleDistributedQueue queue = new SimpleDistributedQueue(firstClient, "/queues/simple");
            assertThat(queue.poll(100, TimeUnit.MILLISECONDS)).isNull();
            assertThat(queue.offer(bytes("alpha"))).isTrue();
            assertThat(queue.offer(bytes("beta"))).isTrue();
            assertThat(string(queue.element())).isEqualTo("alpha");
            assertThat(string(queue.peek())).isEqualTo("alpha");
            assertThat(string(queue.poll())).isEqualTo("alpha");
            assertThat(string(queue.take())).isEqualTo("beta");
            assertThatThrownBy(queue::remove).isInstanceOf(NoSuchElementException.class);

            DistributedBarrier barrier = new DistributedBarrier(firstClient, "/barriers/single");
            barrier.setBarrier();
            assertThat(barrier.waitOnBarrier(100, TimeUnit.MILLISECONDS)).isFalse();
            barrier.removeBarrier();
            assertThat(barrier.waitOnBarrier(5, TimeUnit.SECONDS)).isTrue();

            DistributedDoubleBarrier firstDoubleBarrier = new DistributedDoubleBarrier(
                    firstClient, "/barriers/double", 2);
            DistributedDoubleBarrier secondDoubleBarrier = new DistributedDoubleBarrier(
                    secondClient, "/barriers/double", 2);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                Future<Boolean> firstEnter = executor.submit(() -> firstDoubleBarrier.enter(5, TimeUnit.SECONDS));
                Future<Boolean> secondEnter = executor.submit(() -> secondDoubleBarrier.enter(5, TimeUnit.SECONDS));
                assertThat(firstEnter.get(6, TimeUnit.SECONDS)).isTrue();
                assertThat(secondEnter.get(6, TimeUnit.SECONDS)).isTrue();

                Future<Boolean> firstLeave = executor.submit(() -> firstDoubleBarrier.leave(5, TimeUnit.SECONDS));
                Future<Boolean> secondLeave = executor.submit(() -> secondDoubleBarrier.leave(5, TimeUnit.SECONDS));
                assertThat(firstLeave.get(6, TimeUnit.SECONDS)).isTrue();
                assertThat(secondLeave.get(6, TimeUnit.SECONDS)).isTrue();
            } finally {
                executor.shutdownNow();
                assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            }
        }
    }

    private static CuratorFramework newClient(LocalZooKeeperServer server) throws Exception {
        CuratorFramework client = CuratorFrameworkFactory.newClient(
                server.connectString(), SESSION_TIMEOUT_MS, CONNECTION_TIMEOUT_MS, new RetryOneTime(25));
        client.start();
        if (!client.blockUntilConnected(10, TimeUnit.SECONDS)) {
            client.close();
            throw new AssertionError("Curator client did not connect to the local ZooKeeper server");
        }
        return client;
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static String string(byte[] value) {
        return new String(value, StandardCharsets.UTF_8);
    }

    private static void awaitUntil(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            TimeUnit.MILLISECONDS.sleep(10);
        }
        throw new AssertionError("Condition was not satisfied before the timeout");
    }

    private static void closeQuietly(Closeable closeable) {
        CloseableUtils.closeQuietly(closeable);
    }

    private static void closeLeaderLatchIfStarted(LeaderLatch leaderLatch) throws IOException {
        if (leaderLatch.getState() == LeaderLatch.State.STARTED) {
            leaderLatch.close();
        }
    }

    private static final class LocalZooKeeperServer implements Closeable {
        private final Path dataDirectory;
        private final Path logDirectory;
        private final NIOServerCnxnFactory connectionFactory;
        private final String connectString;

        private LocalZooKeeperServer(Path dataDirectory, Path logDirectory, NIOServerCnxnFactory connectionFactory) {
            this.dataDirectory = dataDirectory;
            this.logDirectory = logDirectory;
            this.connectionFactory = connectionFactory;
            this.connectString = "127.0.0.1:" + connectionFactory.getLocalPort();
        }

        static LocalZooKeeperServer start() throws Exception {
            Path dataDirectory = Files.createTempDirectory("curator-recipes-zk-data");
            Path logDirectory = Files.createTempDirectory("curator-recipes-zk-log");
            ZooKeeperServer zooKeeperServer = new NoJmxZooKeeperServer(
                    dataDirectory.toFile(), logDirectory.toFile(), 200);
            NIOServerCnxnFactory connectionFactory = new NIOServerCnxnFactory();
            connectionFactory.configure(new InetSocketAddress("127.0.0.1", 0), 20);
            connectionFactory.startup(zooKeeperServer);
            return new LocalZooKeeperServer(dataDirectory, logDirectory, connectionFactory);
        }

        String connectString() {
            return connectString;
        }

        @Override
        public void close() throws IOException {
            connectionFactory.shutdown();
            deleteRecursively(dataDirectory);
            deleteRecursively(logDirectory);
        }

        private static void deleteRecursively(Path directory) throws IOException {
            if (Files.notExists(directory)) {
                return;
            }
            try (Stream<Path> paths = Files.walk(directory)) {
                List<Path> orderedPaths = paths.sorted(Comparator.reverseOrder()).toList();
                for (Path path : orderedPaths) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static final class NoJmxZooKeeperServer extends ZooKeeperServer {
        private NoJmxZooKeeperServer(File snapDirectory, File logDirectory, int tickTime) throws IOException {
            super(snapDirectory, logDirectory, tickTime);
        }

        @Override
        protected void registerJMX() {
        }

        @Override
        protected void unregisterJMX() {
        }
    }
}
