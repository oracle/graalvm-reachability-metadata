/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity_dep;

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

import static org.assertj.core.api.Assertions.assertThat;

public class SocketOutputTargetTest {
    @Test
    void sendsLogEventToSocketOutputStream() throws Exception {
        InetAddress loopbackAddress = InetAddress.getLoopbackAddress();
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try (ServerSocket serverSocket = new ServerSocket(0, 1, loopbackAddress)) {
            Future<LogEvent> receivedEvent = executor.submit(() -> receiveLogEvent(serverSocket));
            SocketOutputTarget target = new SocketOutputTarget(loopbackAddress, serverSocket.getLocalPort());

            try {
                LogEvent event = createLogEvent();

                target.processEvent(event);

                LogEvent deserializedEvent = receivedEvent.get(10, TimeUnit.SECONDS);
                assertThat(deserializedEvent.getCategory()).isEqualTo("coverage.socketOutputTarget");
                assertThat(deserializedEvent.getMessage()).isEqualTo("message sent over the socket target");
                assertThat(deserializedEvent.getPriority().getName()).isEqualTo(Priority.INFO.getName());
                assertThat(deserializedEvent.getTime()).isEqualTo(123456789L);
            } finally {
                target.close();
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private static LogEvent receiveLogEvent(ServerSocket serverSocket) throws Exception {
        try (Socket socket = serverSocket.accept()) {
            socket.setSoTimeout(5_000);
            try (ObjectInputStream input = new ObjectInputStream(socket.getInputStream())) {
                return (LogEvent) input.readObject();
            }
        }
    }

    private static LogEvent createLogEvent() {
        LogEvent event = new LogEvent();
        event.setCategory("coverage.socketOutputTarget");
        event.setMessage("message sent over the socket target");
        event.setPriority(Priority.INFO);
        event.setTime(123456789L);
        return event;
    }
}
