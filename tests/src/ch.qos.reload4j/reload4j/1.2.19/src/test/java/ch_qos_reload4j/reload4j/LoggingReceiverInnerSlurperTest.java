/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_reload4j.reload4j;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ObjectOutputStream;
import java.lang.reflect.Constructor;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.TimeUnit;

import javax.swing.table.TableModel;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.jupiter.api.Test;

public class LoggingReceiverInnerSlurperTest {
    private static final Logger LOGGER = Logger.getLogger(LoggingReceiverInnerSlurperTest.class);
    private static final String LOGGER_FQCN = LoggingReceiverInnerSlurperTest.class.getName();

    @Test
    void receivesSerializedLoggingEventsFromSocketConnections() throws Exception {
        TableModel model = newChainsawTableModel();
        int port = findAvailablePort();
        Thread receiver = newLoggingReceiver(model, port);
        receiver.start();

        String message = "chainsaw socket event";
        sendLoggingEvent(port, new LoggingEvent(LOGGER_FQCN, LOGGER, Level.INFO, message, null));

        assertReceivedMessage(model, message);
    }

    private static TableModel newChainsawTableModel() throws Exception {
        Constructor<?> constructor = Class.forName("org.apache.log4j.chainsaw.MyTableModel").getDeclaredConstructor();
        constructor.setAccessible(true);
        return (TableModel) constructor.newInstance();
    }

    private static Thread newLoggingReceiver(TableModel model, int port) throws Exception {
        Class<?> modelClass = Class.forName("org.apache.log4j.chainsaw.MyTableModel");
        Constructor<?> constructor = Class.forName("org.apache.log4j.chainsaw.LoggingReceiver")
                .getDeclaredConstructor(modelClass, int.class);
        constructor.setAccessible(true);
        return (Thread) constructor.newInstance(model, port);
    }

    private static int findAvailablePort() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return serverSocket.getLocalPort();
        }
    }

    private static void sendLoggingEvent(int port, LoggingEvent event) throws Exception {
        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), port);
                ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream())) {
            output.writeObject(event);
            output.flush();
        }
    }

    private static void assertReceivedMessage(TableModel model, String expectedMessage) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        Object receivedMessage = null;
        while (System.nanoTime() < deadline) {
            if (model.getRowCount() > 0) {
                receivedMessage = model.getValueAt(0, 5);
                if (expectedMessage.equals(receivedMessage)) {
                    return;
                }
            }
            Thread.sleep(50);
        }
        assertThat(receivedMessage).as("received Chainsaw event message").isEqualTo(expectedMessage);
    }
}
