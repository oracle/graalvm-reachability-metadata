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
import org.junit.jupiter.api.Test;

public class ApacheHttpSenderTest {

    @Test
    void xmlConfiguredApacheHttpClient4SenderPushesBatch() throws Exception {
        try (LokiPushServer server = LokiPushServer.start()) {
            LoggerContext context = LogbackConfigurations.configure("""
                    <configuration>
                      <appender name="LOKI" class="com.github.loki4j.logback.Loki4jAppender">
                        <http>
                          <url>%s</url>
                          <tenantId>apache-tenant</tenantId>
                          <connectionTimeoutMs>10000</connectionTimeoutMs>
                          <requestTimeoutMs>10000</requestTimeoutMs>
                          <sender class="com.github.loki4j.logback.ApacheHttpSender">
                            <maxConnections>2</maxConnections>
                            <connectionKeepAliveMs>30000</connectionKeepAliveMs>
                          </sender>
                        </http>
                        <batch>
                          <maxItems>1</maxItems>
                          <timeoutMs>10000</timeoutMs>
                        </batch>
                        <labels>client = apache4</labels>
                        <message>
                          <pattern>%%level %%msg</pattern>
                        </message>
                      </appender>
                      <root level="INFO">
                        <appender-ref ref="LOKI"/>
                      </root>
                    </configuration>
                    """.formatted(server.pushUrl()));

            Logger logger = context.getLogger("apache.client");
            logger.info("sent through httpclient 4");

            LokiPushServer.PushRequest request = server.takeRequest();
            context.stop();

            assertThat(request.method()).isEqualTo("POST");
            assertThat(request.header("Content-Type")).startsWith("application/json");
            assertThat(request.header("X-Scope-OrgID")).isEqualTo("apache-tenant");
            assertThat(request.body())
                    .contains("\"client\":\"apache4\"")
                    .contains("INFO sent through httpclient 4");
        }
    }
}
