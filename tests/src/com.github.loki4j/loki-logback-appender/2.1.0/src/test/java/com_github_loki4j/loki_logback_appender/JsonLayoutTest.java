/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_loki4j.loki_logback_appender;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import com.github.loki4j.logback.JsonLayout;
import com.github.loki4j.logback.json.LoggerNameJsonProvider;
import com.github.loki4j.logback.json.MdcJsonProvider;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.slf4j.event.KeyValuePair;

public class JsonLayoutTest {

    @Test
    void rendersStandardFieldsMdcKeyValuePairsAndStackTrace() {
        LoggerContext context = LogbackConfigurations.newContext();
        JsonLayout layout = new JsonLayout();
        layout.setContext(context);
        layout.start();

        Logger logger = context.getLogger("inventory.sync");
        LoggingEvent event = new LoggingEvent(
                Logger.class.getName(),
                logger,
                Level.ERROR,
                "sync failed for {}",
                new IllegalStateException("warehouse offline"),
                new Object[] {"sku-7"});
        event.setThreadName("sync-worker");
        event.setMDCPropertyMap(Map.of("requestId", "r-1"));
        event.setKeyValuePairs(List.of(new KeyValuePair("attempt", 3)));

        String json = layout.doLayout(event);
        layout.stop();

        assertThat(layout.getContentType()).isEqualTo("application/json");
        assertThat(json)
                .startsWith("{")
                .endsWith("}")
                .contains("\"timestamp_ms\":" + event.getTimeStamp())
                .contains("\"logger_name\":\"inventory.sync\"")
                .contains("\"level\":\"ERROR\"")
                .contains("\"thread_name\":\"sync-worker\"")
                .contains("\"message\":\"sync failed for sku-7\"")
                .contains("\"mdc_requestId\":\"r-1\"")
                .contains("\"kvp_attempt\":3")
                .contains("\"stack_trace\":\"java.lang.IllegalStateException: warehouse offline");
    }

    @Test
    void appliesCustomizedProviders() {
        LoggerContext context = LogbackConfigurations.newContext();
        LoggerNameJsonProvider loggerName = new LoggerNameJsonProvider();
        loggerName.setFieldName("source");
        MdcJsonProvider mdc = new MdcJsonProvider();
        mdc.setNoPrefix(true);
        mdc.addInclude("tenant");

        JsonLayout layout = new JsonLayout();
        layout.setContext(context);
        layout.setLoggerName(loggerName);
        layout.setMdc(mdc);
        layout.start();

        LoggingEvent event = new LoggingEvent(
                Logger.class.getName(),
                context.getLogger("tenant.audit"),
                Level.INFO,
                "audit recorded",
                null,
                null);
        event.setMDCPropertyMap(Map.of("tenant", "acme", "secret", "hidden"));

        String json = layout.doLayout(event);
        layout.stop();

        assertThat(json)
                .contains("\"source\":\"tenant.audit\"")
                .contains("\"tenant\":\"acme\"")
                .contains("\"message\":\"audit recorded\"")
                .doesNotContain("logger_name")
                .doesNotContain("hidden");
    }
}
