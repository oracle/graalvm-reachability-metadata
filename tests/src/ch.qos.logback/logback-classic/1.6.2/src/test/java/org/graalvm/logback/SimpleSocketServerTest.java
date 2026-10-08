/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.logback;

import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.net.SimpleSocketServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

public class SimpleSocketServerTest {

  @TempDir
  Path temporaryDirectory;

  @Test
  void commandLineConfiguresAndCreatesRequestedServerType() throws Exception {
    Path configuration = temporaryDirectory.resolve("logback-server.xml");
    Files.writeString(configuration, """
        <configuration>
          <root level="OFF"/>
        </configuration>
        """);
    LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
    InspectableSimpleSocketServer.clearInstance();

    try {
      InspectableSimpleSocketServer.runMain(new String[] {
          "0", configuration.toString(), InetAddress.getLoopbackAddress().getHostAddress()
      });

      InspectableSimpleSocketServer server = InspectableSimpleSocketServer.instance();
      assertThat(server.loggerContext).isSameAs(loggerContext);
      assertThat(server.port).isZero();
      assertThat(server.started).isTrue();
      assertThat(server.acceptsLoopback()).isTrue();
      assertThat(loggerContext.getLogger(Logger.ROOT_LOGGER_NAME).getLevel()).isEqualTo(Level.OFF);
    } finally {
      loggerContext.reset();
      InspectableSimpleSocketServer.clearInstance();
    }
  }

  public static final class InspectableSimpleSocketServer extends SimpleSocketServer {

    private static final AtomicReference<InspectableSimpleSocketServer> INSTANCE = new AtomicReference<>();

    private final LoggerContext loggerContext;
    private final int port;
    private boolean started;

    public InspectableSimpleSocketServer(LoggerContext loggerContext, int port) {
      super(loggerContext, port);
      this.loggerContext = loggerContext;
      this.port = port;
      INSTANCE.set(this);
    }

    static void runMain(String[] arguments) throws Exception {
      doMain(InspectableSimpleSocketServer.class, arguments);
    }

    static InspectableSimpleSocketServer instance() {
      return INSTANCE.get();
    }

    static void clearInstance() {
      INSTANCE.set(null);
    }

    @Override
    public synchronized void start() {
      started = true;
    }

    boolean acceptsLoopback() {
      return isClientAllowed(InetAddress.getLoopbackAddress());
    }
  }
}
