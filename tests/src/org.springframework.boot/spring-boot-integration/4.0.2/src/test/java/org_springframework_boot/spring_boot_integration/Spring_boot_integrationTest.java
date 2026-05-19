/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_integration;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.source.MutuallyExclusiveConfigurationPropertiesException;
import org.springframework.boot.integration.autoconfigure.IntegrationAutoConfiguration;
import org.springframework.boot.integration.autoconfigure.IntegrationProperties;
import org.springframework.boot.integration.autoconfigure.PollerMetadataCustomizer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.integration.annotation.Gateway;
import org.springframework.integration.annotation.IntegrationComponentScan;
import org.springframework.integration.annotation.MessagingGateway;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.context.IntegrationContextUtils;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.scheduling.PollerMetadata;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.PollableChannel;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.PeriodicTrigger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Spring_boot_integrationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(IntegrationAutoConfiguration.class))
            .withPropertyValues(commonProperties());

    @Test
    void integrationPropertiesExposeDefaultsAndStoreConfiguredValues() {
        IntegrationProperties properties = new IntegrationProperties();

        assertThat(properties.getChannel().isAutoCreate()).isTrue();
        assertThat(properties.getChannel().getMaxUnicastSubscribers()).isEqualTo(Integer.MAX_VALUE);
        assertThat(properties.getChannel().getMaxBroadcastSubscribers()).isEqualTo(Integer.MAX_VALUE);
        assertThat(properties.getEndpoint().getDefaultTimeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(properties.getEndpoint().getReadOnlyHeaders()).isEmpty();
        assertThat(properties.getEndpoint().getNoAutoStartup()).isEmpty();
        assertThat(properties.getError().isRequireSubscribers()).isTrue();
        assertThat(properties.getError().isIgnoreFailures()).isTrue();
        assertThat(properties.getPoller().getMaxMessagesPerPoll()).isEqualTo(Integer.MIN_VALUE);
        assertThat(properties.getPoller().getReceiveTimeout()).isEqualTo(Duration.ofSeconds(1));
        assertThat(properties.getManagement().isDefaultLoggingEnabled()).isTrue();
        assertThat(properties.getManagement().getObservationPatterns()).isEmpty();

        properties.getChannel().setAutoCreate(false);
        properties.getChannel().setMaxUnicastSubscribers(3);
        properties.getChannel().setMaxBroadcastSubscribers(4);
        properties.getEndpoint().setThrowExceptionOnLateReply(true);
        properties.getEndpoint().setDefaultTimeout(Duration.ofMillis(750));
        properties.getEndpoint().setReadOnlyHeaders(List.of("id", "timestamp"));
        properties.getEndpoint().setNoAutoStartup(List.of("inbound*", "manualEndpoint"));
        properties.getError().setRequireSubscribers(false);
        properties.getError().setIgnoreFailures(false);
        properties.getPoller().setMaxMessagesPerPoll(5);
        properties.getPoller().setReceiveTimeout(Duration.ofMillis(25));
        properties.getPoller().setFixedRate(Duration.ofSeconds(2));
        properties.getPoller().setInitialDelay(Duration.ofMillis(100));
        properties.getPoller().setCron("*/5 * * * * *");
        properties.getManagement().setDefaultLoggingEnabled(false);
        properties.getManagement().setObservationPatterns(List.of("flow*", "!noisy*"));
        properties.getRsocket().getClient().setHost("localhost");
        properties.getRsocket().getClient().setPort(7000);
        properties.getRsocket().getClient().setUri(URI.create("tcp://localhost:7001"));
        properties.getRsocket().getServer().setMessageMappingEnabled(true);

        assertThat(properties.getChannel().isAutoCreate()).isFalse();
        assertThat(properties.getChannel().getMaxUnicastSubscribers()).isEqualTo(3);
        assertThat(properties.getChannel().getMaxBroadcastSubscribers()).isEqualTo(4);
        assertThat(properties.getEndpoint().isThrowExceptionOnLateReply()).isTrue();
        assertThat(properties.getEndpoint().getDefaultTimeout()).isEqualTo(Duration.ofMillis(750));
        assertThat(properties.getEndpoint().getReadOnlyHeaders()).containsExactly("id", "timestamp");
        assertThat(properties.getEndpoint().getNoAutoStartup()).containsExactly("inbound*", "manualEndpoint");
        assertThat(properties.getError().isRequireSubscribers()).isFalse();
        assertThat(properties.getError().isIgnoreFailures()).isFalse();
        assertThat(properties.getPoller().getMaxMessagesPerPoll()).isEqualTo(5);
        assertThat(properties.getPoller().getReceiveTimeout()).isEqualTo(Duration.ofMillis(25));
        assertThat(properties.getPoller().getFixedRate()).isEqualTo(Duration.ofSeconds(2));
        assertThat(properties.getPoller().getInitialDelay()).isEqualTo(Duration.ofMillis(100));
        assertThat(properties.getPoller().getCron()).isEqualTo("*/5 * * * * *");
        assertThat(properties.getManagement().isDefaultLoggingEnabled()).isFalse();
        assertThat(properties.getManagement().getObservationPatterns()).containsExactly("flow*", "!noisy*");
        assertThat(properties.getRsocket().getClient().getHost()).isEqualTo("localhost");
        assertThat(properties.getRsocket().getClient().getPort()).isEqualTo(7000);
        assertThat(properties.getRsocket().getClient().getUri()).isEqualTo(URI.create("tcp://localhost:7001"));
        assertThat(properties.getRsocket().getServer().isMessageMappingEnabled()).isTrue();
    }

    @Test
    void autoConfigurationBindsBootPropertiesAndMapsThemToSpringIntegrationGlobalProperties() {
        runner(IntegrationTestApplication.class,
                "spring.integration.channel.auto-create=false",
                "spring.integration.channel.max-unicast-subscribers=6",
                "spring.integration.channel.max-broadcast-subscribers=7",
                "spring.integration.endpoint.throw-exception-on-late-reply=true",
                "spring.integration.endpoint.default-timeout=1500ms",
                "spring.integration.endpoint.read-only-headers=foo,bar",
                "spring.integration.endpoint.no-auto-startup=inputAdapter,scheduled*",
                "spring.integration.error.require-subscribers=false",
                "spring.integration.error.ignore-failures=false")
            .run((context) -> {

            IntegrationProperties bootProperties = context.getBean(IntegrationProperties.class);
            org.springframework.integration.context.IntegrationProperties integrationProperties = context
                    .getBean(org.springframework.integration.context.IntegrationProperties.class);

            assertThat(bootProperties.getChannel().isAutoCreate()).isFalse();
            assertThat(bootProperties.getEndpoint().getReadOnlyHeaders()).containsExactly("foo", "bar");
            assertThat(bootProperties.getEndpoint().getNoAutoStartup()).containsExactly("inputAdapter", "scheduled*");
            assertThat(integrationProperties.isChannelsAutoCreate()).isFalse();
            assertThat(integrationProperties.getChannelsMaxUnicastSubscribers()).isEqualTo(6);
            assertThat(integrationProperties.getChannelsMaxBroadcastSubscribers()).isEqualTo(7);
            assertThat(integrationProperties.isMessagingTemplateThrowExceptionOnLateReply()).isTrue();
            assertThat(integrationProperties.getEndpointsDefaultTimeout()).isEqualTo(1500L);
            assertThat(integrationProperties.getReadOnlyHeaders()).containsExactly("foo", "bar");
            assertThat(integrationProperties.getNoAutoStartupEndpoints()).containsExactly("inputAdapter", "scheduled*");
            assertThat(integrationProperties.isErrorChannelRequireSubscribers()).isFalse();
            assertThat(integrationProperties.isErrorChannelIgnoreFailures()).isFalse();
        });
    }

    @Test
    void defaultPollerMetadataUsesConfiguredTriggerTimeoutAndOrderedCustomizers() {
        runner(PollerApplication.class,
                "spring.integration.poller.fixed-rate=250ms",
                "spring.integration.poller.initial-delay=50ms",
                "spring.integration.poller.max-messages-per-poll=8",
                "spring.integration.poller.receive-timeout=40ms")
            .run((context) -> {

            PollerMetadata pollerMetadata = context.getBean(PollerMetadata.DEFAULT_POLLER_METADATA_BEAN_NAME,
                    PollerMetadata.class);
            PeriodicTrigger trigger = (PeriodicTrigger) pollerMetadata.getTrigger();

            assertThat(trigger.getPeriodDuration()).isEqualTo(Duration.ofMillis(250));
            assertThat(trigger.getInitialDelayDuration()).isEqualTo(Duration.ofMillis(50));
            assertThat(trigger.isFixedRate()).isTrue();
            assertThat(pollerMetadata.getReceiveTimeout()).isEqualTo(40L);
            assertThat(pollerMetadata.getMaxMessagesPerPoll()).isEqualTo(10L);
        });
    }

    @Test
    void defaultPollerRejectsMutuallyExclusiveTriggerProperties() {
        runner(PollerApplication.class,
                "spring.integration.poller.fixed-rate=250ms",
                "spring.integration.poller.fixed-delay=250ms")
            .run((context) -> assertThat(context.getStartupFailure())
                    .hasRootCauseInstanceOf(MutuallyExclusiveConfigurationPropertiesException.class));
    }

    @Test
    void autoConfigurationProvidesTaskSchedulerForIntegrationInfrastructure() throws InterruptedException {
        runner(TaskSchedulerApplication.class)
            .run((context) -> {
            TaskScheduler taskScheduler = context.getBean(IntegrationContextUtils.TASK_SCHEDULER_BEAN_NAME,
                    TaskScheduler.class);
            CountDownLatch taskRan = new CountDownLatch(1);
            ScheduledFuture<?> future = taskScheduler.schedule(taskRan::countDown, Instant.now().plusMillis(10));

            assertThat(taskScheduler).isInstanceOf(ThreadPoolTaskScheduler.class);
            assertThat(future.isCancelled()).isFalse();
            assertThat(taskRan.await(1, TimeUnit.SECONDS)).isTrue();
        });
    }

    @Test
    void integrationFlowBeanIsDiscoveredAndProcessesMessages() {
        runner(FlowApplication.class)
            .run((context) -> {
            MessageChannel requests = context.getBean("requests", MessageChannel.class);
            PollableChannel replies = context.getBean("replies", PollableChannel.class);

            boolean sent = requests.send(MessageBuilder.withPayload("  native image  ").build(), 1_000L);
            Message<?> reply = replies.receive(1_000L);

            assertThat(sent).isTrue();
            assertThat(reply).isNotNull();
            assertThat(reply.getPayload()).isEqualTo("NATIVE IMAGE");
        });
    }

    @Test
    void autoConfigurationScansMessagingGateways() {
        runner(GatewayApplication.class)
            .run((context) -> {
            TextGateway gateway = context.getBean(TextGateway.class);

            String reply = gateway.clean("  component scan  ");

            assertThat(reply).isEqualTo("component scan");
        });
    }

    private ApplicationContextRunner runner(Class<?> source, String... properties) {
        return this.contextRunner.withUserConfiguration(source).withPropertyValues(properties);
    }

    private static String[] commonProperties() {
        return new String[] {
            "spring.jmx.enabled=false",
            "spring.main.lazy-initialization=false"
        };
    }

    @Configuration(proxyBeanMethods = false)
    public static class IntegrationTestApplication {
    }

    public static class FirstPollerCustomizer implements PollerMetadataCustomizer, Ordered {

        @Override
        public void customize(PollerMetadata pollerMetadata) {
            pollerMetadata.setMaxMessagesPerPoll(pollerMetadata.getMaxMessagesPerPoll() + 1);
        }

        @Override
        public int getOrder() {
            return 0;
        }
    }

    public static class SecondPollerCustomizer implements PollerMetadataCustomizer, Ordered {

        @Override
        public void customize(PollerMetadata pollerMetadata) {
            pollerMetadata.setMaxMessagesPerPoll(pollerMetadata.getMaxMessagesPerPoll() + 1);
        }

        @Override
        public int getOrder() {
            return 1;
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class PollerApplication {

        @Bean
        public FirstPollerCustomizer firstPollerCustomizer() {
            return new FirstPollerCustomizer();
        }

        @Bean
        public SecondPollerCustomizer secondPollerCustomizer() {
            return new SecondPollerCustomizer();
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class TaskSchedulerApplication {
    }

    @Configuration(proxyBeanMethods = false)
    public static class FlowApplication {

        @Bean
        public QueueChannel replies() {
            return new QueueChannel(1);
        }

        @Bean
        public IntegrationFlow uppercaseFlow() {
            return IntegrationFlow.from("requests")
                    .<String, String>transform((payload) -> payload.trim().toUpperCase(Locale.ROOT))
                    .channel("replies")
                    .get();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @IntegrationComponentScan(basePackageClasses = TextGateway.class)
    public static class GatewayApplication {

        @Bean
        public TextHandler textHandler() {
            return new TextHandler();
        }
    }

    @MessagingGateway
    public interface TextGateway {

        @Gateway(requestChannel = "textRequests")
        String clean(String text);
    }

    public static class TextHandler {

        @ServiceActivator(inputChannel = "textRequests")
        public String clean(String text) {
            return text.trim();
        }
    }
}
