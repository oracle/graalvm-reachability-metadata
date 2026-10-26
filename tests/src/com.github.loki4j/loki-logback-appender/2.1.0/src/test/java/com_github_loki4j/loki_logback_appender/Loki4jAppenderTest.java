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
import ch.qos.logback.classic.PatternLayout;
import com.github.loki4j.logback.JavaHttpSender;
import com.github.loki4j.logback.Loki4jAppender;
import com.github.loki4j.logback.PipelineConfigAppenderBase.BatchCfg;
import com.github.loki4j.logback.PipelineConfigAppenderBase.HttpCfg;
import com.github.loki4j.slf4j.marker.LabelMarker;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

public class Loki4jAppenderTest {

    @Test
    void xmlConfiguredAppenderPushesJsonBatchWithTenantAndBasicAuth() throws Exception {
        try (LokiPushServer server = LokiPushServer.start()) {
            String configuration = """
                    <configuration>
                      <appender name="LOKI" class="com.github.loki4j.logback.Loki4jAppender">
                        <http>
                          <url>%s</url>
                          <tenantId>tenant-a</tenantId>
                          <connectionTimeoutMs>10000</connectionTimeoutMs>
                          <requestTimeoutMs>10000</requestTimeoutMs>
                          <auth>
                            <username>loki-user</username>
                            <password>loki-secret</password>
                          </auth>
                          <sender class="com.github.loki4j.logback.JavaHttpSender">
                            <innerThreadsExpirationMs>60000</innerThreadsExpirationMs>
                          </sender>
                        </http>
                        <batch>
                          <maxItems>2</maxItems>
                          <timeoutMs>10000</timeoutMs>
                          <useDirectBuffers>false</useDirectBuffers>
                        </batch>
                        <labels>
                          app = orders
                          level = %%level
                        </labels>
                        <structuredMetadata>logger = %%logger</structuredMetadata>
                        <message class="com.github.loki4j.logback.JsonLayout">
                          <loggerName>
                            <fieldName>logger</fieldName>
                          </loggerName>
                          <threadName>
                            <enabled>false</enabled>
                          </threadName>
                        </message>
                      </appender>
                      <root level="INFO">
                        <appender-ref ref="LOKI"/>
                      </root>
                    </configuration>
                    """.formatted(server.pushUrl());
            LoggerContext context = LogbackConfigurations.configure(configuration);

            Logger logger = context.getLogger("orders.service");
            assertThat(context.getLogger(Logger.ROOT_LOGGER_NAME).getAppender("LOKI"))
                    .isInstanceOf(Loki4jAppender.class);
            logger.info("order created");
            logger.info("order paid");

            LokiPushServer.PushRequest request = server.takeRequest();
            context.stop();

            assertThat(request.method()).isEqualTo("POST");
            assertThat(request.header("Content-Type")).isEqualTo("application/json");
            assertThat(request.header("X-Scope-OrgID")).isEqualTo("tenant-a");
            String expectedToken = Base64.getEncoder()
                    .encodeToString("loki-user:loki-secret".getBytes(StandardCharsets.UTF_8));
            assertThat(request.header("Authorization")).isEqualTo("Basic " + expectedToken);
            assertThat(request.body())
                    .contains("\"streams\"")
                    .contains("\"app\":\"orders\"")
                    .contains("\"level\":\"INFO\"")
                    .contains("\"logger\":\"orders.service\"")
                    .contains("order created")
                    .contains("order paid")
                    .doesNotContain("thread_name");
        }
    }

    @Test
    void programmaticAppenderSendsMarkerLabelsAndDrainsOnStop() throws Exception {
        try (LokiPushServer server = LokiPushServer.start()) {
            LoggerContext context = LogbackConfigurations.newContext();
            Loki4jAppender appender = new Loki4jAppender();
            appender.setContext(context);
            appender.setName("programmatic");
            appender.setLabels("service = billing\nlevel = %level");
            appender.setStructuredMetadata("off");
            appender.setReadMarkers(true);
            appender.setHttp(httpConfig(server));
            appender.setBatch(batchConfig(100));

            PatternLayout layout = new PatternLayout();
            layout.setPattern("%msg");
            appender.setMessage(layout);
            appender.start();

            Logger logger = context.getLogger("billing.job");
            logger.addAppender(appender);
            logger.setAdditive(false);
            logger.warn(LabelMarker.of("invoice", () -> "INV-42"), "invoice overdue");

            appender.stop();
            LokiPushServer.PushRequest request = server.takeRequest();
            context.stop();

            assertThat(request.body())
                    .contains("\"service\":\"billing\"")
                    .contains("\"level\":\"WARN\"")
                    .contains("\"invoice\":\"INV-42\"")
                    .contains("invoice overdue");
        }
    }

    @Test
    void retriesBatchWhenLokiIsTemporarilyUnavailable() throws Exception {
        try (LokiPushServer server = LokiPushServer.start()) {
            server.planStatuses(503);
            LoggerContext context = LogbackConfigurations.newContext();
            Loki4jAppender appender = new Loki4jAppender();
            appender.setContext(context);
            appender.setName("retrying");
            appender.setLabels("job = retry");
            HttpCfg http = httpConfig(server);
            http.setMaxRetries(3);
            http.setMinRetryBackoffMs(50);
            http.setMaxRetryBackoffMs(200);
            http.setMaxRetryJitterMs(10);
            appender.setHttp(http);
            appender.setBatch(batchConfig(1));
            appender.start();

            Logger logger = context.getLogger("retry.job");
            logger.addAppender(appender);
            logger.setAdditive(false);
            logger.error("payment gateway unreachable");

            LokiPushServer.PushRequest rejected = server.takeRequest();
            LokiPushServer.PushRequest accepted = server.takeRequest();
            appender.stop();
            context.stop();

            assertThat(rejected.status()).isEqualTo(503);
            assertThat(accepted.status()).isEqualTo(204);
            assertThat(accepted.body()).isEqualTo(rejected.body()).contains("payment gateway unreachable");
        }
    }

    private static HttpCfg httpConfig(LokiPushServer server) {
        HttpCfg http = new HttpCfg();
        http.setUrl(server.pushUrl());
        http.setConnectionTimeoutMs(10_000);
        http.setRequestTimeoutMs(10_000);
        http.setSender(new JavaHttpSender());
        return http;
    }

    private static BatchCfg batchConfig(int maxItems) {
        BatchCfg batch = new BatchCfg();
        batch.setMaxItems(maxItems);
        batch.setTimeoutMs(10_000);
        return batch;
    }
}
