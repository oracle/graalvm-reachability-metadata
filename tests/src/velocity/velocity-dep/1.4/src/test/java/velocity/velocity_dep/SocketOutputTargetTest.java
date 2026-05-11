/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity_dep;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ObjectInputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.log.LogEvent;
import org.apache.log.Priority;
import org.apache.log.output.net.SocketOutputTarget;
import org.junit.jupiter.api.Test;

public class SocketOutputTargetTest {
    @Test
    void writesSerializedLogEventToConnectedSocket() throws Exception {
        final InetAddress loopbackAddress = InetAddress.getLoopbackAddress();
        final ExecutorService executorService = Executors.newSingleThreadExecutor();

        try (ServerSocket serverSocket = new ServerSocket(0, 1, loopbackAddress)) {
            final Future<LogEvent> receivedEventFuture = executorService.submit(() -> receiveLogEvent(serverSocket));
            final SocketOutputTarget target = new SocketOutputTarget(loopbackAddress, serverSocket.getLocalPort());

            try {
                final LogEvent event = new LogEvent();
                event.setCategory("socket.output.target");
                event.setMessage("message sent over object stream");
                event.setPriority(Priority.INFO);
                event.setTime(System.currentTimeMillis());

                target.processEvent(event);

                final LogEvent receivedEvent = receivedEventFuture.get(10, TimeUnit.SECONDS);
                assertThat(receivedEvent.getCategory()).isEqualTo(event.getCategory());
                assertThat(receivedEvent.getMessage()).isEqualTo(event.getMessage());
                assertThat(receivedEvent.getPriority()).isSameAs(Priority.INFO);
                assertThat(receivedEvent.getTime()).isEqualTo(event.getTime());
            } finally {
                target.close();
            }
        } finally {
            executorService.shutdownNow();
        }
    }

    private static LogEvent receiveLogEvent(final ServerSocket serverSocket) throws Exception {
        try (Socket socket = serverSocket.accept()) {
            socket.setSoTimeout(10_000);
            try (ObjectInputStream inputStream = new ObjectInputStream(socket.getInputStream())) {
                final Object receivedObject = inputStream.readObject();
                assertThat(receivedObject).isInstanceOf(LogEvent.class);
                return (LogEvent) receivedObject;
            }
        }
    }
}
