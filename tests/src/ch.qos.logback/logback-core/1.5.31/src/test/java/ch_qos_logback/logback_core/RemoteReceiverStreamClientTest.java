/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_logback.logback_core;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.core.ContextBase;
import ch.qos.logback.core.net.AbstractSocketAppender;
import ch.qos.logback.core.spi.PreSerializationTransformer;
import java.io.ObjectInputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import org.junit.jupiter.api.Test;

public class RemoteReceiverStreamClientTest {

    private static final int TIMEOUT_MILLIS = 5_000;

    @Test
    void writesSerializedEventToConnectedReceiver() throws Exception {
        ContextBase context = new ContextBase();
        context.setName("socket-appender-test-context");
        StringSocketAppender appender = new StringSocketAppender();

        try (ServerSocket serverSocket = new ServerSocket(0, 50,
                InetAddress.getLoopbackAddress())) {
            serverSocket.setSoTimeout(TIMEOUT_MILLIS);
            appender.setContext(context);
            appender.setName("socket-appender-test");
            appender.setRemoteHost(InetAddress.getLoopbackAddress().getHostAddress());
            appender.setPort(serverSocket.getLocalPort());

            appender.start();

            assertThat(appender.isStarted()).isTrue();

            try (Socket receiverSocket = serverSocket.accept()) {
                receiverSocket.setSoTimeout(TIMEOUT_MILLIS);

                try (ObjectInputStream inputStream = new ObjectInputStream(
                        receiverSocket.getInputStream())) {
                    appender.doAppend("remote-receiver-payload");

                    assertThat(inputStream.readObject()).isEqualTo("remote-receiver-payload");
                }
            }
        } finally {
            appender.stop();
            context.stop();
        }
    }

    private static final class StringSocketAppender extends AbstractSocketAppender<String> {

        @Override
        protected void postProcessEvent(String event) {
        }

        @Override
        protected PreSerializationTransformer<String> getPST() {
            return event -> event;
        }
    }
}
