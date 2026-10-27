/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_logstash_logback.logstash_logback_encoder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.util.LogbackMDCAdapter;
import net.logstash.logback.encoder.LogstashEncoder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractFormattedTimestampJsonProviderTest {

  @Test
  void formatsTimestampWithStandardDateTimeFormatterConstant() throws IOException {
    LoggerContext context = new LoggerContext();
    context.setName("formatted-timestamp-provider-test");
    context.setMDCAdapter(new LogbackMDCAdapter());
    LogstashEncoder encoder = new LogstashEncoder();
    encoder.setContext(context);
    encoder.setFindAndRegisterJacksonModules(false);
    encoder.setTimeZone("UTC");
    encoder.setTimestampPattern("[ISO_ORDINAL_DATE]");
    encoder.start();

    try {
      LoggingEvent event = new LoggingEvent(AbstractFormattedTimestampJsonProviderTest.class.getName(),
          context.getLogger(AbstractFormattedTimestampJsonProviderTest.class), Level.INFO, "timestamped event", null,
          null);
      event.setInstant(Instant.parse("2024-02-29T12:34:56Z"));
      ByteArrayOutputStream output = new ByteArrayOutputStream();

      encoder.encode(event, output);

      assertThat(output.toString(StandardCharsets.UTF_8)).contains("\"@timestamp\":\"2024-060Z\"");
    } finally {
      encoder.stop();
      context.stop();
    }
  }
}
