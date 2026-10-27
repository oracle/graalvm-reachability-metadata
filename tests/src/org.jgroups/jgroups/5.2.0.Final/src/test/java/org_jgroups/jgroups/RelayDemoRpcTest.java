/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import org.jgroups.demos.RelayDemoRpc;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class RelayDemoRpcTest {
    static {
        configureJGroupsLoopbackDefaults();
    }

    @BeforeAll
    static void configureLoopbackDefaults() {
        configureJGroupsLoopbackDefaults();
    }

    @TempDir
    Path tempDir;

    @Test
    @ResourceLock("System.in")
    void startsWithLoopbackStackAndCreatesRpcMethodCall() throws Exception {
        Path stackConfiguration = writeLoopbackStackConfiguration();
        CloseableInputStream input = new CloseableInputStream();
        InputStream originalIn = System.in;
        TestableRelayDemoRpc demo = new TestableRelayDemoRpc();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> startup = null;

        try {
            System.setIn(input);
            startup = executor.submit(() -> {
                demo.start(stackConfiguration.toString(), "relay-demo-rpc-test");
                return null;
            });

            assertThat(input.awaitFirstRead()).isTrue();
            assertThat(demo.isConnected()).isTrue();
            assertThat(demo.localAddress()).isNotBlank();

            input.close();
            Future<?> startedDemo = startup;
            assertThatThrownBy(() -> startedDemo.get(10, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(IOException.class);
        } finally {
            input.close();
            demo.closeChannel();
            if (startup != null) {
                startup.cancel(true);
            }
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
            System.setIn(originalIn);
        }
    }

    private Path writeLoopbackStackConfiguration() throws IOException {
        Path configuration = tempDir.resolve("relay-demo-rpc-loopback.xml");
        Files.writeString(configuration, """
                <config xmlns="urn:org:jgroups"
                        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                        xsi:schemaLocation="urn:org:jgroups http://www.jgroups.org/schema/jgroups.xsd">
                    <SHARED_LOOPBACK/>
                    <SHARED_LOOPBACK_PING/>
                    <pbcast.NAKACK2 xmit_interval="500"/>
                    <UNICAST3 xmit_interval="500"/>
                    <pbcast.STABLE desired_avg_gossip="50000" max_bytes="4M"/>
                    <pbcast.GMS print_local_addr="false" join_timeout="10000"/>
                    <FRAG2 frag_size="60K"/>
                </config>
                """);
        return configuration;
    }

    private static void configureJGroupsLoopbackDefaults() {
        System.setProperty("jgroups.bind_addr", "127.0.0.1");
        System.setProperty("java.net.preferIPv4Stack", "true");
        System.setProperty("jgroups.use.jdk_logger", "true");
    }

    public static class TestableRelayDemoRpc extends RelayDemoRpc {
        boolean isConnected() {
            return ch != null && ch.isConnected();
        }

        String localAddress() {
            return local_addr;
        }

        void closeChannel() {
            if (ch != null) {
                ch.close();
            }
        }
    }

    private static class CloseableInputStream extends InputStream {
        private final CountDownLatch firstReadStarted = new CountDownLatch(1);
        private final CountDownLatch closed = new CountDownLatch(1);

        boolean awaitFirstRead() throws InterruptedException {
            return firstReadStarted.await(10, TimeUnit.SECONDS);
        }

        @Override
        public int read() throws IOException {
            firstReadStarted.countDown();
            try {
                closed.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                InterruptedIOException interrupted = new InterruptedIOException(
                        "interrupted while waiting for test input");
                interrupted.initCause(e);
                throw interrupted;
            }
            throw new IOException("test input closed");
        }

        @Override
        public int available() {
            return 0;
        }

        @Override
        public void close() {
            closed.countDown();
        }
    }
}
