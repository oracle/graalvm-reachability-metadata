/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_reload4j.reload4j;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.time.Duration;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.chainsaw.LoggingReceiverHarness;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LoggingReceiverInnerSlurperTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final int CONNECT_RETRY_MILLIS = 25;

    @Test
    void readsSerializedLoggingEventFromAcceptedSocket() throws Exception {
        boolean receiverClosedSocket = LoggingReceiverHarness.runWithReceiver(port -> {
            try (Socket socket = connectToReceiver(port);
                    ObjectOutputStream outputStream = new ObjectOutputStream(socket.getOutputStream())) {
                outputStream.writeObject(createLoggingEvent());
                outputStream.flush();
                socket.shutdownOutput();

                return waitUntilReceiverCloses(socket);
            }
        });

        assertThat(receiverClosedSocket).isTrue();
    }

    private static LoggingEvent createLoggingEvent() {
        return new LoggingEvent(
                LoggingReceiverInnerSlurperTest.class.getName(),
                Logger.getLogger("reload4j.chainsaw.receiver"),
                Level.INFO,
                "chainsaw socket event",
                null);
    }

    private static Socket connectToReceiver(int port) throws Exception {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        IOException lastException = null;

        while (System.nanoTime() < deadline) {
            Socket socket = new Socket();
            try {
                socket.connect(new InetSocketAddress("127.0.0.1", port), CONNECT_RETRY_MILLIS);
                return socket;
            } catch (IOException e) {
                lastException = e;
                socket.close();
                Thread.sleep(CONNECT_RETRY_MILLIS);
            }
        }

        throw lastException == null ? new IOException("Timed out connecting to receiver") : lastException;
    }

    private static boolean waitUntilReceiverCloses(Socket socket) throws IOException {
        socket.setSoTimeout((int) TIMEOUT.toMillis());
        try {
            return socket.getInputStream().read() == -1;
        } catch (EOFException e) {
            return true;
        } catch (SocketTimeoutException e) {
            return false;
        }
    }
}
