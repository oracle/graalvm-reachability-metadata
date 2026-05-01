/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_lucene.lucene_core;

import static org.assertj.core.api.Assertions.assertThat;

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

public class LockStressTestTest {
    @TempDir
    Path lockDirectory;

    @Test
    public void createsNamedLockFactoryBeforeRunningStressLoop() throws Exception {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try (ServerSocket serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            Future<?> verifier = executorService.submit(() -> {
                try (Socket socket = serverSocket.accept()) {
                    assertThat(socket.getInputStream().read()).isEqualTo(7);
                    socket.getOutputStream().write(43);
                    socket.getOutputStream().flush();
                }
                return null;
            });

            LockStressTest.main(new String[] {
                    "7",
                    InetAddress.getLoopbackAddress().getHostAddress(),
                    Integer.toString(serverSocket.getLocalPort()),
                    SimpleFSLockFactory.class.getName(),
                    lockDirectory.toString(),
                    "0",
                    "0"
            });

            verifier.get(5, TimeUnit.SECONDS);
        } finally {
            executorService.shutdownNow();
        }
    }
}
