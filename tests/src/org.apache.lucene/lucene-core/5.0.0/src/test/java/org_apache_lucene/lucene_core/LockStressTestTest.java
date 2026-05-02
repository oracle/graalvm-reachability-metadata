/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_lucene.lucene_core;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.lucene.store.LockStressTest;
import org.apache.lucene.store.SimpleFSLockFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class LockStressTestTest {
    private static final int CLIENT_ID = 17;
    private static final int STARTING_GUN = 43;
    private static final int LOCKED = 1;
    private static final int UNLOCKED = 0;

    @TempDir
    Path lockDirectory;

    @Test
    void instantiatesConfiguredLockFactoryAndCompletesSingleLockCycle() throws Exception {
        try (LockVerificationServer server = LockVerificationServer.start()) {
            LockStressTest.main(new String[] {
                    Integer.toString(CLIENT_ID),
                    InetAddress.getLoopbackAddress().getHostAddress(),
                    Integer.toString(server.port()),
                    SimpleFSLockFactory.class.getName(),
                    lockDirectory.toString(),
                    "0",
                    "1"
            });

            server.awaitCompletion();
        }
    }

    private static final class LockVerificationServer implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final ExecutorService executorService;
        private final Future<?> serverFuture;

        private LockVerificationServer(
                ServerSocket serverSocket,
                ExecutorService executorService,
                Future<?> serverFuture) {
            this.serverSocket = serverSocket;
            this.executorService = executorService;
            this.serverFuture = serverFuture;
        }

        private static LockVerificationServer start() throws Exception {
            ServerSocket serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
            ExecutorService executorService = Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "lock-stress-test-verifier");
                thread.setDaemon(true);
                return thread;
            });
            Future<?> serverFuture = executorService.submit(() -> {
                serveSingleClient(serverSocket);
                return null;
            });
            return new LockVerificationServer(serverSocket, executorService, serverFuture);
        }

        private int port() {
            return serverSocket.getLocalPort();
        }

        private void awaitCompletion() throws Exception {
            serverFuture.get(5, TimeUnit.SECONDS);
        }

        @Override
        public void close() throws Exception {
            serverSocket.close();
            executorService.shutdownNow();
            assertThat(executorService.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }

        private static void serveSingleClient(ServerSocket serverSocket) throws Exception {
            try (Socket socket = serverSocket.accept();
                    InputStream inputStream = socket.getInputStream();
                    OutputStream outputStream = socket.getOutputStream()) {
                assertThat(inputStream.read()).isEqualTo(CLIENT_ID);
                outputStream.write(STARTING_GUN);
                outputStream.flush();

                boolean locked = false;
                int transitions = 0;
                int command;
                while ((command = inputStream.read()) >= 0) {
                    if (command == LOCKED) {
                        assertThat(locked).isFalse();
                        locked = true;
                    } else {
                        assertThat(command).isEqualTo(UNLOCKED);
                        assertThat(locked).isTrue();
                        locked = false;
                    }
                    transitions++;
                    outputStream.write(command);
                    outputStream.flush();
                }

                assertThat(locked).isFalse();
                assertThat(transitions).isEqualTo(2);
            }
        }
    }
}
