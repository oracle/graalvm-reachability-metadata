/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.logback;

import java.util.HashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.LoggingEventVO;
import ch.qos.logback.core.AppenderBase;

import static org.assertj.core.api.Assertions.assertThat;

final class RemoteLoggingSupport implements AutoCloseable {

  static final int TIMEOUT_MILLIS = 20_000;

  final LoggerContext context = new LoggerContext();
  final Logger logger = context.getLogger("remote.orders");
  private final CapturingAppender appender = new CapturingAppender();

  RemoteLoggingSupport() {
    context.setName("remote-context");
    logger.setLevel(Level.INFO);
    logger.setAdditive(false);
    appender.setContext(context);
    appender.start();
    logger.addAppender(appender);
  }

  LoggingEventVO event(Level level, String message, Object... arguments) {
    LoggingEvent event = new LoggingEvent(RemoteLoggingSupport.class.getName(), logger,
        level, message, null, arguments);
    HashMap<String, String> mdc = new HashMap<>();
    mdc.put("request", "remote-request");
    event.setMDCPropertyMap(mdc);
    event.setThreadName("remote-worker");
    return LoggingEventVO.build(event);
  }

  ILoggingEvent awaitEvent() throws InterruptedException {
    ILoggingEvent event = appender.events.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
    assertThat(event).as("event delivered to the local logger").isNotNull();
    return event;
  }

  @Override
  public void close() {
    logger.detachAppender(appender);
    appender.stop();
    context.stop();
  }

  private static final class CapturingAppender extends AppenderBase<ILoggingEvent> {

    private final BlockingQueue<ILoggingEvent> events = new LinkedBlockingQueue<>();

    @Override
    protected void append(ILoggingEvent event) {
      events.add(event);
    }
  }
}
