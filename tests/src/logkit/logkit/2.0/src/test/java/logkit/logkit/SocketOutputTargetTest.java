/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package logkit.logkit;

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
    void processEventSendsSerializedLogEventToSocketServer() throws Exception {
        InetAddress loopbackAddress = InetAddress.getLoopbackAddress();
        ExecutorService executorService = Executors.newSingleThreadExecutor();

        try (ServerSocket serverSocket = new ServerSocket(0, 1, loopbackAddress)) {
            Future<LogEvent> receivedEvent = executorService.submit(() -> receiveLogEvent(serverSocket));
            SocketOutputTarget target = new SocketOutputTarget(loopbackAddress, serverSocket.getLocalPort());
            LogEvent logEvent = createLogEvent();

            target.processEvent(logEvent);

            LogEvent deserializedEvent = receivedEvent.get(5, TimeUnit.SECONDS);
            assertThat(deserializedEvent.getCategory()).isEqualTo(logEvent.getCategory());
            assertThat(deserializedEvent.getMessage()).isEqualTo(logEvent.getMessage());
            assertThat(deserializedEvent.getPriority().getName()).isEqualTo(logEvent.getPriority().getName());
            assertThat(deserializedEvent.getTime()).isEqualTo(logEvent.getTime());
        } finally {
            executorService.shutdownNow();
        }
    }

    private static LogEvent createLogEvent() {
        LogEvent logEvent = new LogEvent();
        logEvent.setCategory("socket-output-target");
        logEvent.setMessage("serialized log event");
        logEvent.setPriority(Priority.INFO);
        logEvent.setTime(System.currentTimeMillis());
        return logEvent;
    }

    private static LogEvent receiveLogEvent(ServerSocket serverSocket) throws Exception {
        try (Socket socket = serverSocket.accept();
                ObjectInputStream objectInputStream = new ObjectInputStream(socket.getInputStream())) {
            return (LogEvent) objectInputStream.readObject();
        }
    }
}
