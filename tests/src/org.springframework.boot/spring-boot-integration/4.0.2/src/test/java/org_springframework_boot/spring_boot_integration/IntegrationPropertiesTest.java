/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_integration;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.integration.autoconfigure.IntegrationProperties;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(120)
public class IntegrationPropertiesTest {

    @Test
    void bindsChannelEndpointAndErrorHandlingOptions() {
        MapConfigurationPropertySource source = new MapConfigurationPropertySource(Map.of(
                "spring.integration.channel.auto-create", "false",
                "spring.integration.channel.max-unicast-subscribers", "3",
                "spring.integration.channel.max-broadcast-subscribers", "8",
                "spring.integration.endpoint.default-timeout", "45s",
                "spring.integration.endpoint.throw-exception-on-late-reply", "true",
                "spring.integration.endpoint.read-only-headers", "trackingId,sourceSystem",
                "spring.integration.endpoint.no-auto-startup", "archive*,audit*",
                "spring.integration.error.require-subscribers", "false",
                "spring.integration.error.ignore-failures", "false"));

        IntegrationProperties properties = new Binder(source).bind("spring.integration", IntegrationProperties.class).get();

        assertThat(properties.getChannel().isAutoCreate()).isFalse();
        assertThat(properties.getChannel().getMaxUnicastSubscribers()).isEqualTo(3);
        assertThat(properties.getChannel().getMaxBroadcastSubscribers()).isEqualTo(8);
        assertThat(properties.getEndpoint().getDefaultTimeout()).isEqualTo(Duration.ofSeconds(45));
        assertThat(properties.getEndpoint().isThrowExceptionOnLateReply()).isTrue();
        assertThat(properties.getEndpoint().getReadOnlyHeaders()).containsExactly("trackingId", "sourceSystem");
        assertThat(properties.getEndpoint().getNoAutoStartup()).containsExactly("archive*", "audit*");
        assertThat(properties.getError().isRequireSubscribers()).isFalse();
        assertThat(properties.getError().isIgnoreFailures()).isFalse();
    }

    @Test
    void bindsPollerTimingAndBatchSize() {
        MapConfigurationPropertySource source = new MapConfigurationPropertySource(Map.of(
                "spring.integration.poller.max-messages-per-poll", "12",
                "spring.integration.poller.receive-timeout", "15s",
                "spring.integration.poller.fixed-delay", "20s",
                "spring.integration.poller.initial-delay", "10s"));

        IntegrationProperties properties = new Binder(source).bind("spring.integration", IntegrationProperties.class).get();

        assertThat(properties.getPoller().getMaxMessagesPerPoll()).isEqualTo(12);
        assertThat(properties.getPoller().getReceiveTimeout()).isEqualTo(Duration.ofSeconds(15));
        assertThat(properties.getPoller().getFixedDelay()).isEqualTo(Duration.ofSeconds(20));
        assertThat(properties.getPoller().getInitialDelay()).isEqualTo(Duration.ofSeconds(10));
        assertThat(properties.getPoller().getFixedRate()).isNull();
        assertThat(properties.getPoller().getCron()).isNull();
    }

    @Test
    void bindsRsocketAddressAndObservationOptions() {
        MapConfigurationPropertySource source = new MapConfigurationPropertySource(Map.of(
                "spring.integration.rsocket.client.host", "localhost",
                "spring.integration.rsocket.client.port", "7000",
                "spring.integration.rsocket.client.uri", "ws://localhost:8080/messages",
                "spring.integration.rsocket.server.message-mapping-enabled", "true",
                "spring.integration.management.default-logging-enabled", "false",
                "spring.integration.management.observation-patterns", "orders*,payments*"));

        IntegrationProperties properties = new Binder(source).bind("spring.integration", IntegrationProperties.class).get();

        assertThat(properties.getRsocket().getClient().getHost()).isEqualTo("localhost");
        assertThat(properties.getRsocket().getClient().getPort()).isEqualTo(7000);
        assertThat(properties.getRsocket().getClient().getUri()).isEqualTo(URI.create("ws://localhost:8080/messages"));
        assertThat(properties.getRsocket().getServer().isMessageMappingEnabled()).isTrue();
        assertThat(properties.getManagement().isDefaultLoggingEnabled()).isFalse();
        assertThat(properties.getManagement().getObservationPatterns()).containsExactly("orders*", "payments*");
    }
}
