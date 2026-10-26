/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_loki4j.loki_logback_appender;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

public class Loki4jMetricsTest {

    @Test
    void reportsPipelineMetricsToMicrometerGlobalRegistry() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        Metrics.addRegistry(registry);
        try (LokiPushServer server = LokiPushServer.start()) {
            LoggerContext context = LogbackConfigurations.configure("""
                    <configuration>
                      <appender name="METERED" class="com.github.loki4j.logback.Loki4jAppender">
                        <metricsEnabled>true</metricsEnabled>
                        <http>
                          <url>%s</url>
                          <connectionTimeoutMs>10000</connectionTimeoutMs>
                          <requestTimeoutMs>10000</requestTimeoutMs>
                        </http>
                        <batch>
                          <maxItems>2</maxItems>
                          <timeoutMs>10000</timeoutMs>
                        </batch>
                        <labels>metrics = on</labels>
                      </appender>
                      <root level="INFO">
                        <appender-ref ref="METERED"/>
                      </root>
                    </configuration>
                    """.formatted(server.pushUrl()));

            Logger logger = context.getLogger("metered.service");
            logger.info("first metered event");
            logger.info("second metered event");

            LokiPushServer.PushRequest request = server.takeRequest();
            context.stop();

            assertThat(request.body()).contains("first metered event").contains("second metered event");
            Counter sentBatches = registry.get("loki4j.send.batches").tag("appender", "METERED").counter();
            Counter encodedBatches = registry.get("loki4j.encode.batches").tag("appender", "METERED").counter();
            Timer appendTime = registry.get("loki4j.append.time").tag("appender", "METERED").timer();
            assertThat(sentBatches.count()).isGreaterThanOrEqualTo(1.0);
            assertThat(encodedBatches.count()).isGreaterThanOrEqualTo(1.0);
            assertThat(appendTime.count()).isEqualTo(2L);
        } finally {
            Metrics.removeRegistry(registry);
            registry.close();
        }
    }
}
