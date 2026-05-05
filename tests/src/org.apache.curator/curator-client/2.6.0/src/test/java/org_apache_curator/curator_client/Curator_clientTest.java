/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_curator.curator_client;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.curator.CuratorZookeeperClient;
import org.apache.curator.RetryLoop;
import org.apache.curator.RetryPolicy;
import org.apache.curator.RetrySleeper;
import org.apache.curator.TimeTrace;
import org.apache.curator.drivers.TracerDriver;
import org.apache.curator.ensemble.exhibitor.DefaultExhibitorRestClient;
import org.apache.curator.ensemble.exhibitor.ExhibitorEnsembleProvider;
import org.apache.curator.ensemble.exhibitor.ExhibitorRestClient;
import org.apache.curator.ensemble.exhibitor.Exhibitors;
import org.apache.curator.ensemble.fixed.FixedEnsembleProvider;
import org.apache.curator.retry.BoundedExponentialBackoffRetry;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.retry.RetryNTimes;
import org.apache.curator.retry.RetryOneTime;
import org.apache.curator.retry.RetryUntilElapsed;
import org.apache.curator.utils.CloseableExecutorService;
import org.apache.curator.utils.CloseableScheduledExecutorService;
import org.apache.curator.utils.CloseableUtils;
import org.apache.curator.utils.DefaultTracerDriver;
import org.apache.curator.utils.PathUtils;
import org.apache.curator.utils.ThreadUtils;
import org.apache.curator.utils.ZKPaths;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.KeeperException.Code;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Curator_clientTest {
    @Test
    void retryPoliciesHonorRetryCountsElapsedTimeAndSleepBounds() {
        RecordingSleeper sleeper = new RecordingSleeper();

        RetryOneTime oneTime = new RetryOneTime(7);
        assertThat(oneTime.getN()).isEqualTo(1);
        assertThat(oneTime.allowRetry(0, 0, sleeper)).isTrue();
        assertThat(oneTime.allowRetry(1, 0, sleeper)).isFalse();
        assertThat(sleeper.sleepDurationsMs()).containsExactly(7L);

        sleeper.clear();
        RetryNTimes nTimes = new RetryNTimes(2, 11);
        assertThat(nTimes.getN()).isEqualTo(2);
        assertThat(nTimes.allowRetry(0, 0, sleeper)).isTrue();
        assertThat(nTimes.allowRetry(1, 0, sleeper)).isTrue();
        assertThat(nTimes.allowRetry(2, 0, sleeper)).isFalse();
        assertThat(sleeper.sleepDurationsMs()).containsExactly(11L, 11L);

        sleeper.clear();
        RetryUntilElapsed untilElapsed = new RetryUntilElapsed(20, 3);
        assertThat(untilElapsed.allowRetry(0, 19, sleeper)).isTrue();
        assertThat(untilElapsed.allowRetry(1, 20, sleeper)).isFalse();
        assertThat(sleeper.sleepDurationsMs()).containsExactly(3L, 3L);

        sleeper.clear();
        ExponentialBackoffRetry exponential = new ExponentialBackoffRetry(1, 3, 2);
        assertThat(exponential.getBaseSleepTimeMs()).isEqualTo(1);
        assertThat(exponential.getN()).isEqualTo(3);
        assertThat(exponential.allowRetry(0, 0, sleeper)).isTrue();
        assertThat(sleeper.sleepDurationsMs().get(0)).isBetween(1L, 2L);

        sleeper.clear();
        BoundedExponentialBackoffRetry bounded = new BoundedExponentialBackoffRetry(100, 25, 3);
        assertThat(bounded.getBaseSleepTimeMs()).isEqualTo(100);
        assertThat(bounded.getMaxSleepTimeMs()).isEqualTo(25);
        assertThat(bounded.getN()).isEqualTo(3);
        assertThat(bounded.allowRetry(0, 0, sleeper)).isTrue();
        assertThat(bounded.allowRetry(1, 0, sleeper)).isTrue();
        assertThat(bounded.allowRetry(2, 0, sleeper)).isTrue();
        assertThat(bounded.allowRetry(3, 0, sleeper)).isFalse();
        assertThat(sleeper.sleepDurationsMs()).containsExactly(25L, 25L, 25L);
    }

    @Test
    void retryLoopRetriesKeeperConnectionFailuresAndRecordsTracerCounts() throws Exception {
        RetryPolicy retryTwiceWithoutSleeping = (retryCount, elapsedTimeMs, sleeper) -> retryCount < 2;
        CuratorZookeeperClient client = new CuratorZookeeperClient(
                "ignored-host:2181", 1_000, 0, null, retryTwiceWithoutSleeping);
        RecordingTracerDriver tracer = new RecordingTracerDriver();
        client.setTracerDriver(tracer);

        AtomicInteger attempts = new AtomicInteger();
        String result = RetryLoop.callWithRetry(client, () -> {
            if (attempts.getAndIncrement() < 2) {
                throw KeeperException.create(Code.CONNECTIONLOSS);
            }
            return "connected-result";
        });

        assertThat(result).isEqualTo("connected-result");
        assertThat(attempts).hasValue(3);
        assertThat(tracer.counts()).containsEntry("retries-allowed", 2);
        assertThat(RetryLoop.shouldRetry(Code.OPERATIONTIMEOUT.intValue())).isTrue();
        assertThat(RetryLoop.shouldRetry(Code.SESSIONEXPIRED.intValue())).isTrue();
        assertThat(RetryLoop.shouldRetry(Code.NONODE.intValue())).isFalse();
        assertThat(RetryLoop.isRetryException(KeeperException.create(Code.SESSIONMOVED))).isTrue();
        assertThat(RetryLoop.isRetryException(new IllegalArgumentException("not from ZooKeeper"))).isFalse();

        RetryLoop loop = client.newRetryLoop();
        assertThat(loop.shouldContinue()).isTrue();
        loop.markComplete();
        assertThat(loop.shouldContinue()).isFalse();
        assertThatThrownBy(() -> client.newRetryLoop().takeException(KeeperException.create(Code.NONODE)))
                .isInstanceOf(KeeperException.NoNodeException.class);

        client.close();
    }

    @Test
    void curatorZookeeperClientExposesConfigurationAndTracingWithoutStartingNetworkConnection() {
        RetryPolicy initialPolicy = new RetryOneTime(1);
        CuratorZookeeperClient client = new CuratorZookeeperClient(
                new FixedEnsembleProvider("host1:2181,host2:2181"), 3_000, 250, event -> { }, initialPolicy);
        RecordingTracerDriver tracer = new RecordingTracerDriver();

        assertThat(client.getCurrentConnectionString()).isEqualTo("host1:2181,host2:2181");
        assertThat(client.getConnectionTimeoutMs()).isEqualTo(250);
        assertThat(client.isConnected()).isFalse();
        assertThat(client.getRetryPolicy()).isSameAs(initialPolicy);
        assertThat(client.getTracerDriver()).isInstanceOf(DefaultTracerDriver.class);
        assertThatThrownBy(client::getZooKeeper).isInstanceOf(IllegalStateException.class);

        RetryPolicy replacementPolicy = new RetryNTimes(3, 1);
        client.setRetryPolicy(replacementPolicy);
        client.setTracerDriver(tracer);
        TimeTrace trace = client.startTracer("configuration-test");
        trace.commit();

        assertThat(client.getRetryPolicy()).isSameAs(replacementPolicy);
        assertThat(client.getTracerDriver()).isSameAs(tracer);
        assertThat(tracer.traces()).containsKey("configuration-test");

        CloseableUtils.closeQuietly(client);
    }

    @Test
    void fixedAndExhibitorEnsembleProvidersBuildConnectionStringsFromPublicInputs() throws Exception {
        FixedEnsembleProvider fixed = new FixedEnsembleProvider("fixed1:2181,fixed2:2181");
        fixed.start();
        assertThat(fixed.getConnectionString()).isEqualTo("fixed1:2181,fixed2:2181");
        fixed.close();

        Exhibitors exhibitors = new Exhibitors(
                Arrays.asList("exhibitor1", "exhibitor2"),
                8_080,
                () -> "backup1:3181, backup2:3181");
        assertThat(exhibitors.getHostnames()).containsExactly("exhibitor1", "exhibitor2");
        assertThat(exhibitors.getRestPort()).isEqualTo(8_080);
        assertThat(exhibitors.getBackupConnectionString()).isEqualTo("backup1:3181, backup2:3181");

        AtomicInteger rawRequests = new AtomicInteger();
        AtomicReference<String> requestedHost = new AtomicReference<>();
        AtomicInteger requestedPort = new AtomicInteger();
        AtomicReference<String> requestedUriPath = new AtomicReference<>();
        AtomicReference<String> requestedMimeType = new AtomicReference<>();
        ExhibitorRestClient restClient = (hostname, port, uriPath, mimeType) -> {
            rawRequests.incrementAndGet();
            requestedHost.set(hostname);
            requestedPort.set(port);
            requestedUriPath.set(uriPath);
            requestedMimeType.set(mimeType);
            return "count=2&port=2181&server0=zk-one&server1=zk%20two";
        };
        ExhibitorEnsembleProvider provider = new ExhibitorEnsembleProvider(
                exhibitors,
                restClient,
                "/exhibitor/v1/cluster/list",
                60_000,
                new RetryOneTime(1));

        provider.pollForInitialEnsemble();
        assertThat(provider.getConnectionString()).isEqualTo("zk-one:2181,zk two:2181");
        assertThat(rawRequests).hasValue(1);
        assertThat(exhibitors.getHostnames()).contains(requestedHost.get());
        assertThat(requestedPort).hasValue(8_080);
        assertThat(requestedUriPath).hasValue("/exhibitor/v1/cluster/list");
        assertThat(requestedMimeType).hasValue("application/x-www-form-urlencoded");

        provider.setExhibitors(new Exhibitors(Collections.emptyList(), 8_080, () -> "backup1:3181,backup2:3181"));
        provider.pollForInitialEnsemble();
        assertThat(provider.getConnectionString()).isEqualTo("backup1:3181,backup2:3181");
    }

    @Test
    void defaultExhibitorRestClientReadsHttpResponseAndSendsAcceptHeader() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            ExecutorService serverExecutor = Executors.newSingleThreadExecutor(
                    ThreadUtils.newThreadFactory("curator-exhibitor-rest"));
            Future<List<String>> requestLines = serverExecutor.submit(() -> {
                try (Socket socket = serverSocket.accept()) {
                    socket.setSoTimeout((int) TimeUnit.SECONDS.toMillis(5));
                    BufferedReader reader = new BufferedReader(new InputStreamReader(
                            socket.getInputStream(), StandardCharsets.ISO_8859_1));
                    List<String> lines = new ArrayList<>();
                    String line;
                    while ((line = reader.readLine()) != null && !line.isEmpty()) {
                        lines.add(line);
                    }

                    byte[] responseBody = "count=1&port=2181&server0=zk-one".getBytes(StandardCharsets.ISO_8859_1);
                    byte[] responseHeader = ("HTTP/1.1 200 OK\r\n"
                            + "Content-Type: text/plain\r\n"
                            + "Content-Length: " + responseBody.length + "\r\n"
                            + "Connection: close\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1);
                    OutputStream outputStream = socket.getOutputStream();
                    outputStream.write(responseHeader);
                    outputStream.write(responseBody);
                    outputStream.flush();
                    return lines;
                }
            });

            try {
                DefaultExhibitorRestClient restClient = new DefaultExhibitorRestClient();
                String rawResponse = restClient.getRaw(
                        "127.0.0.1",
                        serverSocket.getLocalPort(),
                        "/exhibitor/v1/cluster/list",
                        "application/x-www-form-urlencoded");

                assertThat(rawResponse).isEqualTo("count=1&port=2181&server0=zk-one");
                assertThat(requestLines.get(5, TimeUnit.SECONDS))
                        .contains("GET /exhibitor/v1/cluster/list HTTP/1.1")
                        .anyMatch(header -> header.equalsIgnoreCase("Accept: application/x-www-form-urlencoded"));
            } finally {
                serverSocket.close();
                serverExecutor.shutdownNow();
                assertThat(serverExecutor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            }
        }
    }

    @Test
    void pathUtilitiesNormalizeSplitAndValidateZooKeeperPaths() {
        assertThat(ZKPaths.makePath("base", "child")).isEqualTo("/base/child");
        assertThat(ZKPaths.makePath("/base/", "/child")).isEqualTo("/base/child");
        assertThat(ZKPaths.fixForNamespace("namespace", "/node")).isEqualTo("/namespace/node");
        assertThat(ZKPaths.fixForNamespace(null, "/node")).isEqualTo("/node");
        assertThat(ZKPaths.getNodeFromPath("/one/two/three")).isEqualTo("three");

        ZKPaths.PathAndNode pathAndNode = ZKPaths.getPathAndNode("/one/two/three");
        assertThat(pathAndNode.getPath()).isEqualTo("/one/two");
        assertThat(pathAndNode.getNode()).isEqualTo("three");

        ZKPaths.PathAndNode rootChild = ZKPaths.getPathAndNode("/child");
        assertThat(rootChild.getPath()).isEqualTo("/");
        assertThat(rootChild.getNode()).isEqualTo("child");

        PathUtils.validatePath("/valid/path");
        PathUtils.validatePath("/valid/sequential-", true);
        assertThatThrownBy(() -> PathUtils.validatePath(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PathUtils.validatePath("relative/path")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PathUtils.validatePath("/has//empty-node"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PathUtils.validatePath("/ends/with/slash/"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PathUtils.validatePath("/uses/../relative"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void threadUtilitiesCreateDaemonNamedExecutorsAndResolveProcessNames() throws Exception {
        ThreadFactory factory = ThreadUtils.newThreadFactory("curator-worker");
        AtomicReference<Thread> createdThread = new AtomicReference<>();
        CountDownLatch createdThreadLatch = new CountDownLatch(1);
        Thread thread = factory.newThread(() -> {
            createdThread.set(Thread.currentThread());
            createdThreadLatch.countDown();
        });
        thread.start();

        assertThat(createdThreadLatch.await(5, TimeUnit.SECONDS)).isTrue();
        thread.join(TimeUnit.SECONDS.toMillis(5));
        assertThat(createdThread.get().getName()).startsWith("curator-worker-");
        assertThat(createdThread.get().isDaemon()).isTrue();

        ExecutorService singleThreadExecutor = ThreadUtils.newSingleThreadExecutor("curator-single");
        try {
            Thread executorThread = singleThreadExecutor.submit(Thread::currentThread).get(5, TimeUnit.SECONDS);
            assertThat(executorThread.getName()).startsWith("curator-single-");
            assertThat(executorThread.isDaemon()).isTrue();
        } finally {
            singleThreadExecutor.shutdownNow();
            assertThat(singleThreadExecutor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }

        ExecutorService fixedThreadPool = ThreadUtils.newFixedThreadPool(2, "curator-fixed");
        try {
            Thread executorThread = fixedThreadPool.submit(Thread::currentThread).get(5, TimeUnit.SECONDS);
            assertThat(executorThread.getName()).startsWith("curator-fixed-");
            assertThat(executorThread.isDaemon()).isTrue();
        } finally {
            fixedThreadPool.shutdownNow();
            assertThat(fixedThreadPool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }

        ScheduledExecutorService singleScheduledExecutor = ThreadUtils.newSingleThreadScheduledExecutor(
                "curator-scheduled");
        try {
            Thread scheduledThread = singleScheduledExecutor.schedule(
                    Thread::currentThread, 1, TimeUnit.MILLISECONDS).get(5, TimeUnit.SECONDS);
            assertThat(scheduledThread.getName()).startsWith("curator-scheduled-");
            assertThat(scheduledThread.isDaemon()).isTrue();
        } finally {
            singleScheduledExecutor.shutdownNow();
            assertThat(singleScheduledExecutor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }

        ScheduledExecutorService fixedScheduledPool = ThreadUtils.newFixedThreadScheduledPool(
                2, "curator-fixed-scheduled");
        try {
            Thread scheduledThread = fixedScheduledPool.schedule(
                    Thread::currentThread, 1, TimeUnit.MILLISECONDS).get(5, TimeUnit.SECONDS);
            assertThat(scheduledThread.getName()).startsWith("curator-fixed-scheduled-");
            assertThat(scheduledThread.isDaemon()).isTrue();
        } finally {
            fixedScheduledPool.shutdownNow();
            assertThat(fixedScheduledPool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }

        Object anonymousInstance = new Object() { };
        assertThat(ThreadUtils.getProcessName(Curator_clientTest.class)).isEqualTo("Curator_clientTest");
        assertThat(ThreadUtils.getProcessName(anonymousInstance.getClass())).isEqualTo("Curator_clientTest");
    }

    @Test
    void closeableExecutorsCompleteScheduledWorkAndRejectSubmissionsAfterClose() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor(ThreadUtils.newThreadFactory("curator-closeable"));
        CloseableExecutorService closeableExecutor = new CloseableExecutorService(executor, true);
        AtomicReference<String> executorResult = new AtomicReference<>();
        Future<String> submitted = closeableExecutor.submit(() -> {
            executorResult.set("executor-result");
            return "executor-result";
        });
        assertThat(submitted.get(5, TimeUnit.SECONDS)).isNull();
        assertThat(executorResult).hasValue("executor-result");
        closeableExecutor.close();
        assertThat(closeableExecutor.isShutdown()).isTrue();
        assertThat(executor.isShutdown()).isTrue();
        assertThatThrownBy(() -> closeableExecutor.submit(() -> "rejected"))
                .isInstanceOf(IllegalStateException.class);

        CountDownLatch scheduledLatch = new CountDownLatch(1);
        ScheduledExecutorService scheduledExecutor = Executors.newSingleThreadScheduledExecutor(
                ThreadUtils.newThreadFactory("curator-closeable-scheduled"));
        CloseableScheduledExecutorService closeableScheduled = new CloseableScheduledExecutorService(
                scheduledExecutor, true);
        Future<?> scheduled = closeableScheduled.schedule(scheduledLatch::countDown, 1, TimeUnit.MILLISECONDS);
        assertThat(scheduledLatch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(scheduled.get(5, TimeUnit.SECONDS)).isNull();

        CountDownLatch fixedDelayLatch = new CountDownLatch(1);
        Future<?> fixedDelay = closeableScheduled.scheduleWithFixedDelay(
                fixedDelayLatch::countDown, 1, 1, TimeUnit.MILLISECONDS);
        assertThat(fixedDelayLatch.await(5, TimeUnit.SECONDS)).isTrue();
        closeableScheduled.close();
        assertThat(fixedDelay.isCancelled()).isTrue();
        assertThat(closeableScheduled.isShutdown()).isTrue();
        assertThat(scheduledExecutor.isShutdown()).isTrue();
        assertThatThrownBy(() -> closeableScheduled.schedule(() -> { }, 1, TimeUnit.MILLISECONDS))
                .isInstanceOf(IllegalStateException.class);
    }

    private static final class RecordingSleeper implements RetrySleeper {
        private final List<Long> sleepDurationsMs = new ArrayList<>();

        @Override
        public void sleepFor(long time, TimeUnit unit) {
            sleepDurationsMs.add(unit.toMillis(time));
        }

        List<Long> sleepDurationsMs() {
            return sleepDurationsMs;
        }

        void clear() {
            sleepDurationsMs.clear();
        }
    }

    private static final class RecordingTracerDriver implements TracerDriver {
        private final Map<String, Integer> counts = new ConcurrentHashMap<>();
        private final Map<String, Long> traces = new ConcurrentHashMap<>();

        @Override
        public void addTrace(String name, long time, TimeUnit unit) {
            traces.put(name, unit.toNanos(time));
        }

        @Override
        public void addCount(String name, int increment) {
            counts.merge(name, increment, Integer::sum);
        }

        Map<String, Integer> counts() {
            return counts;
        }

        Map<String, Long> traces() {
            return traces;
        }
    }
}
