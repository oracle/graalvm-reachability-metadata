/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_logback.logback_core;

import ch.qos.logback.core.ContextBase;
import ch.qos.logback.core.net.AbstractSocketAppender;
import ch.qos.logback.core.spi.PreSerializationTransformer;
import org.junit.jupiter.api.Test;

import java.io.ObjectInputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

import static org.assertj.core.api.Assertions.assertThat;

public class RemoteReceiverStreamClientTest {

    private static final int TIMEOUT_MILLIS = 5_000;

    @Test
    void writesSerializedEventToConnectedReceiver() throws Exception {
        ContextBase context = new ContextBase();
        context.setName("remote-receiver-stream-client-test-context");
        StringSocketAppender appender = new StringSocketAppender();
        appender.setContext(context);
        appender.setName("remote-receiver-stream-client-test");
        appender.setRemoteHost(InetAddress.getLoopbackAddress().getHostAddress());

        try (ServerSocket receiverServer = new ServerSocket(0, 1,
                InetAddress.getLoopbackAddress())) {
            receiverServer.setSoTimeout(TIMEOUT_MILLIS);
            appender.setPort(receiverServer.getLocalPort());
            appender.start();

            assertThat(appender.isStarted()).isTrue();

            try (Socket receiverSocket = receiverServer.accept()) {
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
