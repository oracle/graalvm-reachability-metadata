/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.logback.util;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

import static org.assertj.core.api.Assertions.assertThat;

public class EventQueueAppender extends AppenderBase<ILoggingEvent> {

  private final BlockingQueue<ILoggingEvent> events = new LinkedBlockingQueue<>();

  @Override
  protected void append(ILoggingEvent event) {
    events.add(event);
  }

  public ILoggingEvent awaitEvent() throws InterruptedException {
    ILoggingEvent event = events.poll(10, TimeUnit.SECONDS);
    assertThat(event).as("event delivered to the local logger").isNotNull();
    return event;
  }
}
