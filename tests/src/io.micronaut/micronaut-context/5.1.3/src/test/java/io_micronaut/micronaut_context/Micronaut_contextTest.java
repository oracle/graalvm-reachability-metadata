/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micronaut.micronaut_context;

import static org.assertj.core.api.Assertions.assertThat;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertySource;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(55)
public class Micronaut_contextTest {
    private static final String SYSTEM_PROPERTY = "micronaut.context.test.system-property";

    @Test
    void resolvesSystemAndExplicitPropertySourcesThroughApplicationContext() {
        String previousValue = System.getProperty(SYSTEM_PROPERTY);
        System.setProperty(SYSTEM_PROPERTY, "from-system");
        try {
            PropertySource propertySource = PropertySource.of(
                    "integration-test",
                    Map.of("context.message", "from-property-source", "context.retries", "3"));

            try (ApplicationContext context = ApplicationContext.run(propertySource, Environment.TEST)) {
                assertThat(context.isRunning()).isTrue();
                assertThat(context.getEnvironment().getActiveNames()).contains(Environment.TEST);
                assertThat(context.getEnvironment().getProperty(SYSTEM_PROPERTY, String.class))
                        .contains("from-system");
                assertThat(context.getProperty("context.message", String.class))
                        .contains("from-property-source");
                assertThat(context.getProperty("context.retries", Integer.class)).contains(3);
                assertThat(context.resolvePlaceholders("${context.message}"))
                        .contains("from-property-source");
            }
        } finally {
            if (previousValue == null) {
                System.clearProperty(SYSTEM_PROPERTY);
            } else {
                System.setProperty(SYSTEM_PROPERTY, previousValue);
            }
        }
    }

    @Test
    void bindsConfigurationPropertiesToATypedBean() {
        Map<String, Object> properties = Map.of(
                "context.database.url", "jdbc:example",
                "context.database.pool-size", "4");

        try (ApplicationContext context = ApplicationContext.run(properties, Environment.TEST)) {
            DatabaseConfiguration configuration = context.getBean(DatabaseConfiguration.class);

            assertThat(configuration.getUrl()).isEqualTo("jdbc:example");
            assertThat(configuration.getPoolSize()).isEqualTo(4);
        }
    }

    @Test
    void resolvesFactoryBeanWithConstructorInjectionAndConvertedConfiguration() {
        Map<String, Object> properties = Map.of(
                "context.message-prefix", "welcome",
                "context.message-repeat", "2");

        try (ApplicationContext context = ApplicationContext.run(properties, Environment.TEST)) {
            MessageService service = context.getBean(MessageService.class);

            assertThat(service.render("Ada"))
                    .isEqualTo("welcome welcome Ada at 2025-01-15T12:00:00Z");
            assertThat(context.getBeansOfType(MessageService.class)).containsExactly(service);
            assertThat(context.findBean(Clock.class, Qualifiers.byName("fixed")))
                    .isPresent()
                    .get()
                    .isInstanceOf(Clock.class);
        }
    }

    @Test
    void enablesConditionalBeansFromApplicationProperties() {
        try (ApplicationContext enabled = ApplicationContext.run(
                Map.of("context.feature", "enabled"), Environment.TEST)) {
            assertThat(enabled.findBean(FeatureService.class)).isPresent();
            assertThat(enabled.getBean(FeatureService.class).name()).isEqualTo("enabled");
        }

        try (ApplicationContext disabled = ApplicationContext.run(
                Map.of("context.feature", "disabled"), Environment.TEST)) {
            assertThat(disabled.findBean(FeatureService.class)).isEmpty();
        }
    }

    @Test
    void publishesEventsToBeansResolvedFromTheContext() {
        try (ApplicationContext context = ApplicationContext.run(Environment.TEST)) {
            RecordingListener listener = context.getBean(RecordingListener.class);

            context.publishEvent(new ContextEvent("configuration-loaded"));
            context.publishEvent(new ContextEvent("application-ready"));

            assertThat(listener.events).containsExactly("configuration-loaded", "application-ready");
        }
    }

    @Test
    void registersAndResolvesAProgrammaticSingleton() {
        try (ApplicationContext context = ApplicationContext.run()) {
            ProgrammaticBean bean = new ProgrammaticBean("registered-value");
            context.registerSingleton(ProgrammaticBean.class, bean);

            assertThat(context.getBean(ProgrammaticBean.class)).isSameAs(bean);
            assertThat(context.findBean(ProgrammaticBean.class)).containsSame(bean);
            assertThat(context.getBeansOfType(ProgrammaticBean.class)).containsExactly(bean);
        }
    }

    @Test
    void createsQualifiedBeansForEachConfigurationProperty() {
        Map<String, Object> properties = Map.of(
                "context.regions.primary.endpoint", "https://primary.example",
                "context.regions.primary.retries", "2",
                "context.regions.backup.endpoint", "https://backup.example",
                "context.regions.backup.retries", "4");

        try (ApplicationContext context = ApplicationContext.run(properties, Environment.TEST)) {
            assertThat(context.getBeansOfType(RegionClient.class))
                    .extracting(RegionClient::region)
                    .containsExactlyInAnyOrder("primary", "backup");
            assertThat(context.getBean(
                    RegionClient.class, Qualifiers.byName("primary")).describe("health"))
                    .isEqualTo("primary handles health at https://primary.example after 2 retries");
            assertThat(context.getBean(
                    RegionClient.class, Qualifiers.byName("backup")).describe("health"))
                    .isEqualTo("backup handles health at https://backup.example after 4 retries");
        }
    }

    @EachProperty("context.regions")
    public static class RegionConfiguration {
        private final String region;
        private String endpoint;
        private int retries;

        public RegionConfiguration(@Parameter String region) {
            this.region = region;
        }

        public String getRegion() {
            return region;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public int getRetries() {
            return retries;
        }

        public void setRetries(int retries) {
            this.retries = retries;
        }
    }

    @EachBean(RegionConfiguration.class)
    @Singleton
    public static class RegionClient {
        private final RegionConfiguration configuration;

        @Inject
        public RegionClient(RegionConfiguration configuration) {
            this.configuration = configuration;
        }

        public String region() {
            return configuration.getRegion();
        }

        public String describe(String operation) {
            return configuration.getRegion()
                    + " handles "
                    + operation
                    + " at "
                    + configuration.getEndpoint()
                    + " after "
                    + configuration.getRetries()
                    + " retries";
        }
    }

    @Factory
    public static class TestBeanFactory {
        @Singleton
        @Named("fixed")
        public Clock fixedClock() {
            return Clock.fixed(Instant.parse("2025-01-15T12:00:00Z"), ZoneOffset.UTC);
        }
    }

    @ConfigurationProperties("context.database")
    public static class DatabaseConfiguration {
        private String url;
        private int poolSize;

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public int getPoolSize() {
            return poolSize;
        }

        public void setPoolSize(int poolSize) {
            this.poolSize = poolSize;
        }
    }

    @Singleton
    public static class MessageService {
        private final String prefix;
        private final int repeat;
        private final Clock clock;

        @Inject
        public MessageService(
                @Value("${context.message-prefix}") String prefix,
                @Value("${context.message-repeat:1}") int repeat,
                @Named("fixed") Clock clock) {
            this.prefix = prefix;
            this.repeat = repeat;
            this.clock = clock;
        }

        public String render(String name) {
            return String.join(" ", Collections.nCopies(repeat, prefix))
                    + " " + name + " at " + clock.instant();
        }
    }

    @Requires(property = "context.feature", value = "enabled")
    @Singleton
    public static class FeatureService {
        public String name() {
            return "enabled";
        }
    }

    @Singleton
    public static class RecordingListener implements ApplicationEventListener<ContextEvent> {
        private final List<String> events = new ArrayList<>();

        @Override
        public void onApplicationEvent(ContextEvent event) {
            events.add(event.name());
        }
    }

    public record ContextEvent(String name) {}

    public record ProgrammaticBean(String value) {}
}
