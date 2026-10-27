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

public class ApacheHttp5SenderTest {

    @Test
    void xmlConfiguredApacheHttpClient5SenderPushesBatch() throws Exception {
        try (LokiPushServer server = LokiPushServer.start()) {
            LoggerContext context = LogbackConfigurations.configure("""
                    <configuration>
                      <appender name="LOKI" class="com.github.loki4j.logback.Loki4jAppender">
                        <http>
                          <url>%s</url>
                          <connectionTimeoutMs>10000</connectionTimeoutMs>
                          <requestTimeoutMs>10000</requestTimeoutMs>
                          <auth>
                            <username>http5-user</username>
                            <password>http5-secret</password>
                          </auth>
                          <sender class="com.github.loki4j.logback.ApacheHttp5Sender">
                            <maxConnections>2</maxConnections>
                            <connectionKeepAliveMs>30000</connectionKeepAliveMs>
                          </sender>
                        </http>
                        <batch>
                          <maxItems>1</maxItems>
                          <timeoutMs>10000</timeoutMs>
                        </batch>
                        <labels>client = apache5</labels>
                        <message>
                          <pattern>%%logger %%msg</pattern>
                        </message>
                      </appender>
                      <root level="INFO">
                        <appender-ref ref="LOKI"/>
                      </root>
                    </configuration>
                    """.formatted(server.pushUrl()));

            Logger logger = context.getLogger("apache5.client");
            logger.info("sent through httpclient 5");

            LokiPushServer.PushRequest request = server.takeRequest();
            context.stop();

            assertThat(request.method()).isEqualTo("POST");
            assertThat(request.header("Authorization")).startsWith("Basic ");
            assertThat(request.body())
                    .contains("\"client\":\"apache5\"")
                    .contains("apache5.client sent through httpclient 5");
        }
    }
}
