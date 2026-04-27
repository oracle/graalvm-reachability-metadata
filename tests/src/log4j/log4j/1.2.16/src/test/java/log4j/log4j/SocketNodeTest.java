/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package log4j.log4j;

import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Hierarchy;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.net.SocketNode;
import org.apache.log4j.spi.LoggingEvent;
import org.apache.log4j.spi.RootLogger;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SocketNodeTest {

    @Test
    void readsSerializedLoggingEventsFromASocketAndDispatchesThemToTheLocalHierarchy() throws Exception {
        final String loggerName = SocketNodeTest.class.getName() + "." + System.nanoTime();
        final Hierarchy hierarchy = new Hierarchy(new RootLogger(Level.DEBUG));
        final Logger remoteLogger = hierarchy.getLogger(loggerName);
        final RecordingAppender appender = new RecordingAppender();
        remoteLogger.setAdditivity(false);
        remoteLogger.setLevel(Level.DEBUG);
        remoteLogger.addAppender(appender);

        try (ServerSocket serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
             Socket clientSocket = new Socket(InetAddress.getLoopbackAddress(), serverSocket.getLocalPort());
             Socket serverSideSocket = serverSocket.accept();
             ObjectOutputStream objectOutput = new ObjectOutputStream(clientSocket.getOutputStream())) {
            objectOutput.flush();

            final SocketNode socketNode = new SocketNode(serverSideSocket, hierarchy);
            final LoggingEvent event = new LoggingEvent(
                    SocketNodeTest.class.getName(),
                    remoteLogger,
                    Level.INFO,
                    "remote message",
                    null);

            objectOutput.writeObject(event);
            objectOutput.close();

            socketNode.run();

            assertThat(appender.getMessages()).containsExactly("remote message");
            assertThat(appender.getLoggerNames()).containsExactly(loggerName);
        } finally {
            remoteLogger.removeAppender(appender);
        }
    }

    private static final class RecordingAppender extends AppenderSkeleton {
        private final List<String> messages = new ArrayList<>();
        private final List<String> loggerNames = new ArrayList<>();

        @Override
        protected void append(LoggingEvent event) {
            messages.add(event.getRenderedMessage());
            loggerNames.add(event.getLoggerName());
        }

        @Override
        public void close() {
            closed = true;
        }

        @Override
        public boolean requiresLayout() {
            return false;
        }

        private List<String> getMessages() {
            return messages;
        }

        private List<String> getLoggerNames() {
            return loggerNames;
        }
    }
}
