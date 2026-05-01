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
import java.time.Duration;

import javax.swing.table.TableModel;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.jupiter.api.Test;

public class LoggingReceiverInnerSlurperTest {
    private static final Logger LOGGER = Logger.getLogger(LoggingReceiverInnerSlurperTest.class);
    private static final String MESSAGE = "serialized event delivered to chainsaw receiver";

    @Test
    void receivesSerializedLoggingEventFromSocket() throws Exception {
        int port = findAvailablePort();
        Object model = newPackagePrivateInstance("org.apache.log4j.chainsaw.MyTableModel");
        Thread receiver = newLoggingReceiver(model, port);
        receiver.start();

        sendLoggingEvent(port);
        waitUntilRowsAreVisible((TableModel) model);

        TableModel tableModel = (TableModel) model;
        assertThat(tableModel.getValueAt(0, 1)).isEqualTo(Level.INFO);
        assertThat(tableModel.getValueAt(0, 5)).isEqualTo(MESSAGE);
    }

    private static int findAvailablePort() throws Exception {
        InetAddress loopback = InetAddress.getLoopbackAddress();
        try (ServerSocket socket = new ServerSocket(0, 1, loopback)) {
            return socket.getLocalPort();
        }
    }

    private static Object newPackagePrivateInstance(String className) throws Exception {
        Class<?> type = Class.forName(className);
        Constructor<?> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private static Thread newLoggingReceiver(Object model, int port) throws Exception {
        Class<?> modelType = Class.forName("org.apache.log4j.chainsaw.MyTableModel");
        Class<?> receiverType = Class.forName("org.apache.log4j.chainsaw.LoggingReceiver");
        Constructor<?> constructor = receiverType.getDeclaredConstructor(modelType, int.class);
        constructor.setAccessible(true);
        return (Thread) constructor.newInstance(model, port);
    }

    private static void sendLoggingEvent(int port) throws Exception {
        LoggingEvent event = new LoggingEvent(LoggingReceiverInnerSlurperTest.class.getName(), LOGGER, Level.INFO,
                MESSAGE, null);
        InetAddress loopback = InetAddress.getLoopbackAddress();
        try (Socket socket = new Socket(loopback, port);
                ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream())) {
            output.writeObject(event);
            output.flush();
        }
    }

    private static void waitUntilRowsAreVisible(TableModel model) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < deadline) {
            if (model.getRowCount() > 0) {
                return;
            }
            Thread.sleep(100);
        }
        assertThat(model.getRowCount()).isPositive();
    }
}
