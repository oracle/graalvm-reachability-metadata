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

public class AbstractCompositeJsonFormatterTest {

  @Test
  void selectsJsonDataFormatAndEncodesLoggingEvent() throws IOException {
    LoggerContext context = new LoggerContext();
    context.setName("composite-json-formatter-test");
    context.setMDCAdapter(new LogbackMDCAdapter());
    LogstashEncoder encoder = new LogstashEncoder();
    encoder.setContext(context);
    encoder.setFindAndRegisterJacksonModules(false);
    encoder.setDataFormat("json");
    encoder.start();

    try {
      LoggingEvent event = new LoggingEvent(AbstractCompositeJsonFormatterTest.class.getName(),
          context.getLogger(AbstractCompositeJsonFormatterTest.class), Level.INFO, "encoded with JSON", null, null);
      event.setInstant(Instant.parse("2024-02-29T12:34:56Z"));
      ByteArrayOutputStream output = new ByteArrayOutputStream();

      encoder.encode(event, output);

      assertThat(encoder.getDataFormat()).isEqualTo("json");
      assertThat(output.toString(StandardCharsets.UTF_8)).contains("\"message\":\"encoded with JSON\"");
    } finally {
      encoder.stop();
      context.stop();
    }
  }
}
