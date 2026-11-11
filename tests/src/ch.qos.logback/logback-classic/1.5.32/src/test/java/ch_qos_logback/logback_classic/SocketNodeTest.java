/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_logback.logback_classic;

import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.net.SimpleSocketServer;
import ch.qos.logback.classic.net.SocketNode;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.LoggingEventVO;
import ch.qos.logback.classic.util.LogbackMDCAdapter;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SocketNodeTest {

    @Test
    void receivesRemoteEventsAndAppliesTheLocalLoggerLevel() throws Exception {
        LoggerContext context = new LoggerContext();
        context.setName("receiver");
        context.setMDCAdapter(new LogbackMDCAdapter());
        ListAppender<ILoggingEvent> events = new ListAppender<>();
        events.setContext(context);
        events.start();
        Logger logger = context.getLogger("remote.orders");
        logger.setLevel(Level.WARN);
        logger.setAdditive(false);
        logger.addAppender(events);
        try (ExecutorService executor = Executors.newSingleThreadExecutor();
                ServerSocket listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
                Socket sender = new Socket()) {
            listener.setSoTimeout(10_000);
            sender.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), listener.getLocalPort()), 10_000);
            try (Socket receiver = listener.accept()) {
                receiver.setSoTimeout(10_000);
                SimpleSocketServer server = new SimpleSocketServer(context, listener.getLocalPort());
                try {
                    Future<?> reception = executor.submit(new SocketNode(server, receiver, context));
                    try (ObjectOutputStream output = new ObjectOutputStream(sender.getOutputStream())) {
                        output.writeObject(LoggingEventVO.build(new LoggingEvent(Logger.class.getName(), logger,
                                Level.INFO, "filtered remote event", null, null)));
                        output.writeObject(LoggingEventVO.build(new LoggingEvent(Logger.class.getName(), logger,
                                Level.ERROR, "order {} rejected", null, new Object[]{"order-a"})));
                        output.flush();
                    }
                    reception.get(30, TimeUnit.SECONDS);

                    assertThat(events.list).hasSize(1);
                    ILoggingEvent received = events.list.get(0);
                    assertThat(received.getLoggerName()).isEqualTo("remote.orders");
                    assertThat(received.getLevel()).isEqualTo(Level.ERROR);
                    assertThat(received.getFormattedMessage()).isEqualTo("order order-a rejected");
                    assertThat(received.getLoggerContextVO().getName()).isEqualTo("receiver");
                } finally {
                    server.close();
                }
            }
        } finally {
            events.stop();
            context.stop();
        }
    }
}
