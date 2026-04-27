/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package log4j.log4j;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.TimeUnit;
import javax.swing.table.AbstractTableModel;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LoggingReceiverSlurperTest {

    @Test
    void readsSerializedLoggingEventsFromAcceptedSocketConnections() throws Exception {
        NativeImageTestSupport.assumeDesktopToolkitAvailable();

        AbstractTableModel model = newMyTableModel();
        Thread receiver = newLoggingReceiver(model, 0);
        ServerSocket serverSocket = serverSocketOf(receiver);
        String loggerName = LoggingReceiverSlurperTest.class.getName() + "." + System.nanoTime();
        String message = "slurper message";

        receiver.start();
        try {
            sendSerializedEvent(serverSocket.getLocalPort(), loggerName, message);
            awaitRowCount(model, 1);

            assertThat(model.getRowCount()).isEqualTo(1);
            assertThat(model.getValueAt(0, 3)).isEqualTo(loggerName);
            assertThat(model.getValueAt(0, 5)).isEqualTo(message);
        } finally {
            serverSocket.close();
            receiver.join(TimeUnit.SECONDS.toMillis(1));
        }
    }

    private static AbstractTableModel newMyTableModel() throws ReflectiveOperationException {
        Class<?> myTableModelClass = Class.forName("org.apache.log4j.chainsaw.MyTableModel");
        Constructor<?> constructor = myTableModelClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        return (AbstractTableModel) constructor.newInstance();
    }

    private static Thread newLoggingReceiver(AbstractTableModel model, int port) throws ReflectiveOperationException {
        Class<?> loggingReceiverClass = Class.forName("org.apache.log4j.chainsaw.LoggingReceiver");
        Constructor<?> constructor = loggingReceiverClass.getDeclaredConstructor(model.getClass(), int.class);
        constructor.setAccessible(true);
        return (Thread) constructor.newInstance(model, port);
    }

    private static ServerSocket serverSocketOf(Thread receiver) throws ReflectiveOperationException {
        return (ServerSocket) readField(receiver, "mSvrSock");
    }

    private static Object readField(Object target, String fieldName) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void sendSerializedEvent(int port, String loggerName, String message) throws IOException {
        LoggingEvent event = new LoggingEvent(
                LoggingReceiverSlurperTest.class.getName(),
                Logger.getLogger(loggerName),
                Level.INFO,
                message,
                null);

        try (Socket clientSocket = new Socket(InetAddress.getLoopbackAddress(), port);
             ObjectOutputStream objectOutput = new ObjectOutputStream(clientSocket.getOutputStream())) {
            objectOutput.flush();
            objectOutput.writeObject(event);
            objectOutput.flush();
        }
    }

    private static void awaitRowCount(AbstractTableModel model, int expectedRowCount) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            if (model.getRowCount() >= expectedRowCount) {
                return;
            }
            Thread.sleep(50);
        }
    }
}
