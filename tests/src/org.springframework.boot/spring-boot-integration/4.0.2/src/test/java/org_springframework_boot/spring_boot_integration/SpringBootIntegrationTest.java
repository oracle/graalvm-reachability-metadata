/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_integration;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.integration.actuate.endpoint.IntegrationGraphEndpoint;
import org.springframework.boot.integration.actuate.endpoint.IntegrationGraphEndpoint.GraphDescriptor;
import org.springframework.boot.integration.autoconfigure.IntegrationAutoConfiguration;
import org.springframework.boot.integration.autoconfigure.IntegrationJdbcProperties;
import org.springframework.boot.integration.autoconfigure.IntegrationProperties;
import org.springframework.boot.integration.autoconfigure.PollerMetadataCustomizer;
import org.springframework.boot.task.ThreadPoolTaskSchedulerBuilder;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.integration.graph.Graph;
import org.springframework.integration.graph.IntegrationGraphServer;
import org.springframework.integration.scheduling.PollerMetadata;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.PeriodicTrigger;

import static org.assertj.core.api.Assertions.assertThat;

public class SpringBootIntegrationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(IntegrationAutoConfiguration.class));

    @Test
    void integrationPropertiesExposeDocumentedDefaults() {
        IntegrationProperties properties = new IntegrationProperties();

        assertThat(properties.getChannel().isAutoCreate()).isTrue();
        assertThat(properties.getChannel().getMaxUnicastSubscribers()).isEqualTo(Integer.MAX_VALUE);
        assertThat(properties.getChannel().getMaxBroadcastSubscribers()).isEqualTo(Integer.MAX_VALUE);
        assertThat(properties.getEndpoint().isThrowExceptionOnLateReply()).isFalse();
        assertThat(properties.getEndpoint().getReadOnlyHeaders()).isEmpty();
        assertThat(properties.getEndpoint().getNoAutoStartup()).isEmpty();
        assertThat(properties.getEndpoint().getDefaultTimeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(properties.getError().isRequireSubscribers()).isTrue();
        assertThat(properties.getError().isIgnoreFailures()).isTrue();
        assertThat(properties.getPoller().getMaxMessagesPerPoll()).isEqualTo(Integer.MIN_VALUE);
        assertThat(properties.getPoller().getReceiveTimeout()).isEqualTo(Duration.ofSeconds(1));
        assertThat(properties.getPoller().getFixedDelay()).isNull();
        assertThat(properties.getPoller().getFixedRate()).isNull();
        assertThat(properties.getPoller().getInitialDelay()).isNull();
        assertThat(properties.getPoller().getCron()).isNull();
        assertThat(properties.getManagement().isDefaultLoggingEnabled()).isTrue();
        assertThat(properties.getManagement().getObservationPatterns()).isEmpty();
        assertThat(properties.getRsocket().getClient().getHost()).isNull();
        assertThat(properties.getRsocket().getClient().getPort()).isNull();
        assertThat(properties.getRsocket().getClient().getUri()).isNull();
        assertThat(properties.getRsocket().getServer().isMessageMappingEnabled()).isFalse();
    }

    @Test
    void integrationPropertiesRetainConfiguredValues() {
        IntegrationProperties properties = new IntegrationProperties();
        properties.getChannel().setAutoCreate(false);
        properties.getChannel().setMaxUnicastSubscribers(3);
        properties.getChannel().setMaxBroadcastSubscribers(5);
        properties.getEndpoint().setThrowExceptionOnLateReply(true);
        properties.getEndpoint().setReadOnlyHeaders(List.of("id", "timestamp"));
        properties.getEndpoint().setNoAutoStartup(List.of("inbound*", "poller"));
        properties.getEndpoint().setDefaultTimeout(Duration.ofMillis(250));
        properties.getError().setRequireSubscribers(false);
        properties.getError().setIgnoreFailures(false);
        properties.getPoller().setMaxMessagesPerPoll(7);
        properties.getPoller().setReceiveTimeout(Duration.ofMillis(125));
        properties.getPoller().setFixedDelay(Duration.ofSeconds(2));
        properties.getPoller().setInitialDelay(Duration.ofMillis(50));
        properties.getPoller().setCron("0/5 * * * * *");
        properties.getManagement().setDefaultLoggingEnabled(false);
        properties.getManagement().setObservationPatterns(List.of("flow*", "handler"));
        properties.getRsocket().getClient().setHost("localhost");
        properties.getRsocket().getClient().setPort(7000);
        URI uri = URI.create("ws://localhost:7001/rsocket");
        properties.getRsocket().getClient().setUri(uri);
        properties.getRsocket().getServer().setMessageMappingEnabled(true);

        assertThat(properties.getChannel().isAutoCreate()).isFalse();
        assertThat(properties.getChannel().getMaxUnicastSubscribers()).isEqualTo(3);
        assertThat(properties.getChannel().getMaxBroadcastSubscribers()).isEqualTo(5);
        assertThat(properties.getEndpoint().isThrowExceptionOnLateReply()).isTrue();
        assertThat(properties.getEndpoint().getReadOnlyHeaders()).containsExactly("id", "timestamp");
        assertThat(properties.getEndpoint().getNoAutoStartup()).containsExactly("inbound*", "poller");
        assertThat(properties.getEndpoint().getDefaultTimeout()).isEqualTo(Duration.ofMillis(250));
        assertThat(properties.getError().isRequireSubscribers()).isFalse();
        assertThat(properties.getError().isIgnoreFailures()).isFalse();
        assertThat(properties.getPoller().getMaxMessagesPerPoll()).isEqualTo(7);
        assertThat(properties.getPoller().getReceiveTimeout()).isEqualTo(Duration.ofMillis(125));
        assertThat(properties.getPoller().getFixedDelay()).isEqualTo(Duration.ofSeconds(2));
        assertThat(properties.getPoller().getInitialDelay()).isEqualTo(Duration.ofMillis(50));
        assertThat(properties.getPoller().getCron()).isEqualTo("0/5 * * * * *");
        assertThat(properties.getManagement().isDefaultLoggingEnabled()).isFalse();
        assertThat(properties.getManagement().getObservationPatterns()).containsExactly("flow*", "handler");
        assertThat(properties.getRsocket().getClient().getHost()).isEqualTo("localhost");
        assertThat(properties.getRsocket().getClient().getPort()).isEqualTo(7000);
        assertThat(properties.getRsocket().getClient().getUri()).isEqualTo(uri);
        assertThat(properties.getRsocket().getServer().isMessageMappingEnabled()).isTrue();
    }

    @Test
    void jdbcPropertiesProvideIntegrationSchemaDefault() {
        IntegrationJdbcProperties properties = new IntegrationJdbcProperties();

        assertThat(properties.getDefaultSchemaLocation())
                .isEqualTo("classpath:org/springframework/integration/jdbc/schema-@@platform@@.sql");
    }

    @Test
    void autoConfigurationBindsIntegrationPropertiesAndMapsThemToSpringIntegration() {
        this.contextRunner.withPropertyValues(
                "spring.integration.channel.auto-create=false",
                "spring.integration.channel.max-unicast-subscribers=2",
                "spring.integration.channel.max-broadcast-subscribers=4",
                "spring.integration.endpoint.throw-exception-on-late-reply=true",
                "spring.integration.endpoint.default-timeout=150ms",
                "spring.integration.endpoint.read-only-headers=id,timestamp",
                "spring.integration.endpoint.no-auto-startup=inbound*,poller",
                "spring.integration.error.require-subscribers=false",
                "spring.integration.error.ignore-failures=false")
            .run((context) -> {
                assertThat(context).hasSingleBean(IntegrationProperties.class);
                assertThat(context).hasBean("integrationGlobalProperties");
                IntegrationProperties boundProperties = context.getBean(IntegrationProperties.class);
                org.springframework.integration.context.IntegrationProperties integrationProperties = context
                        .getBean("integrationGlobalProperties",
                                org.springframework.integration.context.IntegrationProperties.class);

                assertThat(boundProperties.getChannel().isAutoCreate()).isFalse();
                assertThat(boundProperties.getEndpoint().getReadOnlyHeaders()).containsExactly("id", "timestamp");
                assertThat(integrationProperties.isChannelsAutoCreate()).isFalse();
                assertThat(integrationProperties.getChannelsMaxUnicastSubscribers()).isEqualTo(2);
                assertThat(integrationProperties.getChannelsMaxBroadcastSubscribers()).isEqualTo(4);
                assertThat(integrationProperties.isMessagingTemplateThrowExceptionOnLateReply()).isTrue();
                assertThat(integrationProperties.getEndpointsDefaultTimeout()).isEqualTo(150L);
                assertThat(integrationProperties.getReadOnlyHeaders()).containsExactly("id", "timestamp");
                assertThat(integrationProperties.getNoAutoStartupEndpoints()).containsExactly("inbound*", "poller");
                assertThat(integrationProperties.isErrorChannelRequireSubscribers()).isFalse();
                assertThat(integrationProperties.isErrorChannelIgnoreFailures()).isFalse();
            });
    }

    @Test
    void autoConfigurationCreatesDefaultPollerAndAppliesCustomizers() {
        PollerMetadataCustomizer customizer = (metadata) -> metadata.setMaxMessagesPerPoll(9);

        this.contextRunner.withBean(PollerMetadataCustomizer.class, () -> customizer)
            .withPropertyValues(
                    "spring.integration.poller.fixed-rate=250ms",
                    "spring.integration.poller.initial-delay=25ms",
                    "spring.integration.poller.receive-timeout=75ms")
            .run((context) -> {
                PollerMetadata metadata = context.getBean(PollerMetadata.DEFAULT_POLLER_METADATA_BEAN_NAME,
                        PollerMetadata.class);

                assertThat(metadata.getMaxMessagesPerPoll()).isEqualTo(9L);
                assertThat(metadata.getReceiveTimeout()).isEqualTo(75L);
                assertThat(metadata.getTrigger()).isInstanceOf(PeriodicTrigger.class);
                PeriodicTrigger trigger = (PeriodicTrigger) metadata.getTrigger();
                assertThat(trigger.isFixedRate()).isTrue();
                assertThat(trigger.getPeriodDuration()).isEqualTo(Duration.ofMillis(250));
                assertThat(trigger.getInitialDelayDuration()).isEqualTo(Duration.ofMillis(25));
            });
    }

    @Test
    void autoConfigurationCreatesTaskSchedulerFromBuilderWhenMissing() {
        this.contextRunner
            .withBean(ThreadPoolTaskSchedulerBuilder.class,
                    () -> new ThreadPoolTaskSchedulerBuilder().poolSize(2))
            .run((context) -> {
                assertThat(context).hasBean("taskScheduler");
                assertThat(context).hasSingleBean(ThreadPoolTaskScheduler.class);

                ThreadPoolTaskScheduler scheduler = context.getBean("taskScheduler", ThreadPoolTaskScheduler.class);
                assertThat(scheduler.getScheduledThreadPoolExecutor().getCorePoolSize()).isEqualTo(2);
            });
    }

    @Test
    void integrationGraphEndpointReturnsCurrentGraphAndCanRebuildIt() {
        TestIntegrationGraphServer graphServer = new TestIntegrationGraphServer();
        IntegrationGraphEndpoint endpoint = new IntegrationGraphEndpoint(graphServer);

        GraphDescriptor initial = endpoint.graph();
        endpoint.rebuild();
        GraphDescriptor rebuilt = endpoint.graph();

        assertThat(initial.getContentDescriptor()).containsEntry("state", "initial");
        assertThat(initial.getNodes()).isEmpty();
        assertThat(initial.getLinks()).isEmpty();
        assertThat(rebuilt.getContentDescriptor()).containsEntry("state", "rebuilt");
        assertThat(graphServer.rebuildCount).isEqualTo(1);
    }

    @Test
    void binderConfiguresManagementAndRSocketProperties() {
        MapConfigurationPropertySource source = new MapConfigurationPropertySource(Map.of(
                "spring.integration.management.default-logging-enabled", "false",
                "spring.integration.management.observation-patterns[0]", "flow*",
                "spring.integration.management.observation-patterns[1]", "handler.input",
                "spring.integration.rsocket.client.host", "localhost",
                "spring.integration.rsocket.client.port", "7000",
                "spring.integration.rsocket.client.uri", "ws://localhost:7001/rsocket",
                "spring.integration.rsocket.server.message-mapping-enabled", "true"));
        IntegrationProperties properties = new Binder(source)
                .bindOrCreate("spring.integration", Bindable.of(IntegrationProperties.class));

        assertThat(properties.getManagement().isDefaultLoggingEnabled()).isFalse();
        assertThat(properties.getManagement().getObservationPatterns()).containsExactly("flow*", "handler.input");
        assertThat(properties.getRsocket().getClient().getHost()).isEqualTo("localhost");
        assertThat(properties.getRsocket().getClient().getPort()).isEqualTo(7000);
        assertThat(properties.getRsocket().getClient().getUri()).isEqualTo(URI.create("ws://localhost:7001/rsocket"));
        assertThat(properties.getRsocket().getServer().isMessageMappingEnabled()).isTrue();
    }

    static final class TestIntegrationGraphServer extends IntegrationGraphServer {

        private Graph graph = new Graph(Map.of("state", "initial"), List.of(), List.of());

        private int rebuildCount;

        @Override
        public Graph getGraph() {
            return this.graph;
        }

        @Override
        public Graph rebuild() {
            this.rebuildCount++;
            this.graph = new Graph(Map.of("state", "rebuilt"), List.of(), List.of());
            return this.graph;
        }

    }

}
