/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_cloud.spring_cloud_context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cloud.bootstrap.config.BootstrapPropertySource;
import org.springframework.cloud.bootstrap.config.PropertySourceBootstrapProperties;
import org.springframework.cloud.bootstrap.config.SimpleBootstrapPropertySource;
import org.springframework.cloud.context.encrypt.EncryptorFactory;
import org.springframework.cloud.context.encrypt.KeyFormatException;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.cloud.context.environment.EnvironmentManager;
import org.springframework.cloud.context.named.NamedContextFactory;
import org.springframework.cloud.context.named.NamedContextFactory.Specification;
import org.springframework.cloud.context.refresh.ContextRefresher;
import org.springframework.cloud.context.scope.GenericScope;
import org.springframework.cloud.context.scope.StandardScopeCache;
import org.springframework.cloud.context.scope.refresh.RefreshScope;
import org.springframework.cloud.context.scope.thread.ThreadScope;
import org.springframework.cloud.endpoint.RefreshEndpoint;
import org.springframework.cloud.endpoint.event.RefreshEvent;
import org.springframework.cloud.endpoint.event.RefreshEventListener;
import org.springframework.cloud.util.PropertyUtils;
import org.springframework.cloud.util.random.CachedRandomPropertySource;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.ResolvableType;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.security.crypto.encrypt.TextEncryptor;

public class Spring_cloud_contextTest {

    @Test
    void environmentManagerPublishesChangesAndResetsManagedPropertySource() {
        StandardEnvironment environment = new StandardEnvironment();
        EnvironmentManager manager = new EnvironmentManager(environment);
        List<ApplicationEvent> events = new ArrayList<>();
        manager.setApplicationEventPublisher(event -> events.add((ApplicationEvent) event));

        manager.setProperty("spring.cloud.context.test.feature.flag", "enabled");
        manager.setProperty("spring.cloud.context.test.feature.flag", "enabled");
        manager.setProperty("spring.cloud.context.test.service.timeout", "PT1S");

        assertThat(manager.getProperty("spring.cloud.context.test.feature.flag")).isEqualTo("enabled");
        assertThat(environment.getPropertySources().contains("manager")).isTrue();
        assertThat(events).hasSize(2);
        assertThat(environmentChangeKeys(events.get(0))).containsExactly("spring.cloud.context.test.feature.flag");
        assertThat(environmentChangeKeys(events.get(1))).containsExactly("spring.cloud.context.test.service.timeout");

        Map<String, Object> removedProperties = manager.reset();

        assertThat(removedProperties).containsEntry("spring.cloud.context.test.feature.flag", "enabled")
                .containsEntry("spring.cloud.context.test.service.timeout", "PT1S");
        assertThat(manager.getProperty("spring.cloud.context.test.feature.flag")).isNull();
        assertThat(events).hasSize(3);
        assertThat(environmentChangeKeys(events.get(2))).containsExactlyInAnyOrder(
                "spring.cloud.context.test.feature.flag", "spring.cloud.context.test.service.timeout");
    }

    @Test
    void bootstrapPropertySourcesDelegateNamesValuesAndPropertyEnumeration() {
        MapPropertySource mapPropertySource = new MapPropertySource("testSource", Map.of(
                "alpha", "one",
                "bravo", "two"));
        BootstrapPropertySource<Map<String, Object>> enumerableBootstrapSource = new BootstrapPropertySource<>(
                mapPropertySource);
        SimpleBootstrapPropertySource<Map<String, Object>> simpleBootstrapSource = new SimpleBootstrapPropertySource<>(
                mapPropertySource);

        assertThat(enumerableBootstrapSource.getName()).isEqualTo("bootstrapProperties-testSource");
        assertThat(enumerableBootstrapSource.getDelegate()).isSameAs(mapPropertySource);
        assertThat(enumerableBootstrapSource.getProperty("alpha")).isEqualTo("one");
        assertThat(enumerableBootstrapSource.getPropertyNames()).containsExactlyInAnyOrder("alpha", "bravo");
        assertThat(simpleBootstrapSource.getName()).isEqualTo("bootstrapProperties-testSource");
        assertThat(simpleBootstrapSource.getDelegate()).isSameAs(mapPropertySource);
        assertThat(simpleBootstrapSource.getProperty("bravo")).isEqualTo("two");
    }

    @Test
    void cachedRandomPropertySourceCachesValuesPerKeyUntilCacheIsCleared() {
        CachedRandomPropertySource.clearCache();
        Map<String, Object> randomValues = new HashMap<>();
        randomValues.put("random.int", 4);
        CachedRandomPropertySource cachedRandom = new CachedRandomPropertySource(
                new MapPropertySource("random", randomValues));

        Object first = cachedRandom.getProperty("cachedrandom.cacheA.int");
        randomValues.put("random.int", 8);
        Object cached = cachedRandom.getProperty("cachedrandom.cacheA.int");
        Object secondKey = cachedRandom.getProperty("cachedrandom.cacheB.int");
        CachedRandomPropertySource.clearCache();
        Object refreshed = cachedRandom.getProperty("cachedrandom.cacheA.int");

        assertThat(first).isEqualTo(4);
        assertThat(cached).isEqualTo(4);
        assertThat(secondKey).isEqualTo(8);
        assertThat(refreshed).isEqualTo(8);
        assertThat(cachedRandom.getProperty("random.int")).isNull();
        assertThat(cachedRandom.getProperty("cachedrandom.int")).isNull();
        CachedRandomPropertySource.clearCache();
    }

    @Test
    void encryptorFactoryCreatesSymmetricTextEncryptorAndRejectsPublicKeys() {
        EncryptorFactory factory = new EncryptorFactory("01234567");
        TextEncryptor encryptor = factory.create("test-password");

        String encrypted = encryptor.encrypt("spring-cloud-context secret");

        assertThat(encrypted).isNotEqualTo("spring-cloud-context secret");
        assertThat(encryptor.decrypt(encrypted)).isEqualTo("spring-cloud-context secret");
        assertThatThrownBy(() -> factory.create("ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQCtest"))
                .isInstanceOf(KeyFormatException.class);
    }

    @Test
    void scopeCacheAndGenericScopesReuseAndDestroyScopedObjects() {
        StandardScopeCache cache = new StandardScopeCache();
        assertThat(cache.put("name", "first")).isEqualTo("first");
        assertThat(cache.put("name", "second")).isEqualTo("first");
        assertThat(cache.get("name")).isEqualTo("first");
        assertThat(cache.remove("name")).isEqualTo("first");
        assertThat(cache.clear()).isEmpty();

        GenericScope scope = new GenericScope();
        scope.setName("scenario");
        AtomicInteger created = new AtomicInteger();
        AtomicInteger destroyed = new AtomicInteger();
        Object first = scope.get("scopedBean", () -> "instance-" + created.incrementAndGet());
        Object second = scope.get("scopedBean", () -> "instance-" + created.incrementAndGet());
        scope.registerDestructionCallback("scopedBean", destroyed::incrementAndGet);

        assertThat(scope.getConversationId()).isEqualTo("scenario");
        assertThat(first).isEqualTo("instance-1");
        assertThat(second).isSameAs(first);
        assertThat(created).hasValue(1);

        scope.destroy();

        assertThat(destroyed).hasValue(1);
        assertThat(scope.getErrors()).isEmpty();
        assertThat(scope.get("scopedBean", () -> "instance-" + created.incrementAndGet())).isEqualTo("instance-2");

        ThreadScope threadScope = new ThreadScope();
        assertThat(threadScope.getConversationId()).isEqualTo("thread");
        assertThat(threadScope.get("threadBean", () -> "thread-instance")).isEqualTo("thread-instance");
        assertThat(threadScope.remove("threadBean")).isEqualTo("thread-instance");
    }

    @Test
    void namedContextFactoryCreatesIsolatedContextsWithProvidersAndResolvableTypes() {
        Map<String, ApplicationContextInitializer<GenericApplicationContext>> initializers = Map.of(
                "alpha", Spring_cloud_contextTest::initializeNamedContext,
                "beta", Spring_cloud_contextTest::initializeNamedContext);
        TestNamedContextFactory factory = new TestNamedContextFactory(initializers);

        try (GenericApplicationContext parent = new GenericApplicationContext()) {
            parent.refresh();
            factory.setApplicationContext(parent);
            factory.setConfigurations(
                    List.of(new TestSpecification("default.shared", DefaultNamedContextConfiguration.class)));

            ClientSettings alphaSettings = factory.getInstance("alpha", ClientSettings.class);
            ClientSettings betaSettings = factory.getProvider("beta", ClientSettings.class).getObject();
            ObjectProvider<ClientSettings> lazyAlphaProvider = factory.getLazyProvider("alpha", ClientSettings.class);
            ClientService resolvedService = factory.getInstance("alpha", ClientService.class, new Class<?>[0]);
            ClientService annotatedService = factory.getAnnotatedInstance("alpha",
                    ResolvableType.forClass(ClientService.class), PreferredClient.class);
            Map<String, ClientService> services = factory.getInstances("alpha", ClientService.class);

            assertThat(factory.getParent()).isSameAs(parent);
            assertThat(alphaSettings.name()).isEqualTo("alpha");
            assertThat(betaSettings.name()).isEqualTo("beta");
            assertThat(lazyAlphaProvider.getObject().name()).isEqualTo("alpha");
            assertThat(resolvedService.description()).isIn("service-alpha", "preferred-alpha");
            assertThat(annotatedService.description()).isEqualTo("preferred-alpha");
            assertThat(services).containsKeys("clientService", "preferredClientService");
            assertThat(factory.getConfigurations()).containsKey("default.shared");
            assertThat(factory.getContextNames()).containsExactlyInAnyOrder("alpha", "beta");
        }
        finally {
            factory.destroy();
        }

        assertThat(factory.getContextNames()).isEmpty();
    }

    @Test
    void refreshEventListenerRefreshesOnlyAfterApplicationIsReady() {
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.refresh();
            RefreshScope refreshScope = new RefreshScope();
            refreshScope.setApplicationContext(context);
            RefreshEventListener listener = new RefreshEventListener(new StubContextRefresher(context, refreshScope,
                    Map.of("spring.cloud.context.test.refresh.event", "ready")));
            RefreshEvent refreshEvent = new RefreshEvent(this, Map.of("trigger", "test"), "test refresh event");

            assertThat(listener.supportsEventType(RefreshEvent.class)).isTrue();
            assertThat(listener.supportsEventType(ApplicationReadyEvent.class)).isTrue();
            assertThat(refreshEvent.getEvent()).isEqualTo(Map.of("trigger", "test"));
            assertThat(refreshEvent.getEventDesc()).isEqualTo("test refresh event");

            listener.onApplicationEvent(refreshEvent);

            assertThat(context.getEnvironment().getProperty("spring.cloud.context.test.refresh.event")).isNull();

            listener.onApplicationEvent(new ApplicationReadyEvent(new SpringApplication(Spring_cloud_contextTest.class),
                    new String[0], context, Duration.ZERO));
            listener.onApplicationEvent(refreshEvent);

            assertThat(context.getEnvironment().getProperty("spring.cloud.context.test.refresh.event"))
                    .isEqualTo("ready");
        }
    }

    @Test
    void propertyUtilitiesPropertiesAndRefreshEndpointExposeSimplePublicContracts() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("defaults", Map.of(
                "spring.config.use-legacy-processing", "false")));
        assertThat(PropertyUtils.useLegacyProcessing(environment)).isFalse();

        environment.getPropertySources().addFirst(new MapPropertySource("cloud", Map.of(
                "spring.cloud.bootstrap.enabled", "true",
                "spring.config.use-legacy-processing", "true")));

        assertThat(PropertyUtils.bootstrapEnabled(environment)).isTrue();
        assertThat(PropertyUtils.useLegacyProcessing(environment)).isTrue();

        PropertySourceBootstrapProperties properties = new PropertySourceBootstrapProperties();
        properties.setAllowOverride(true);
        properties.setOverrideNone(true);
        properties.setOverrideSystemProperties(false);
        properties.setInitializeOnContextRefresh(true);

        assertThat(properties.isAllowOverride()).isTrue();
        assertThat(properties.isOverrideNone()).isTrue();
        assertThat(properties.isOverrideSystemProperties()).isFalse();
        assertThat(properties.isInitializeOnContextRefresh()).isTrue();

        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.refresh();
            RefreshScope refreshScope = new RefreshScope();
            refreshScope.setApplicationContext(context);
            RefreshEndpoint endpoint = new RefreshEndpoint(new StubContextRefresher(context, refreshScope,
                    Map.of("spring.cloud.context.test.alpha", "one", "spring.cloud.context.test.bravo", "two")));

            assertThat(endpoint.refresh()).containsExactlyInAnyOrder("spring.cloud.context.test.alpha",
                    "spring.cloud.context.test.bravo");
        }
    }

    private static Set<String> environmentChangeKeys(ApplicationEvent event) {
        assertThat(event).isInstanceOf(EnvironmentChangeEvent.class);
        return ((EnvironmentChangeEvent) event).getKeys();
    }

    private static void initializeNamedContext(GenericApplicationContext context) {
        String name = context.getEnvironment().getRequiredProperty("client.name");
        context.registerBean(ClientSettings.class, () -> new ClientSettings(name));
        context.registerBean("clientService", ClientService.class, () -> new ClientService("service-" + name));
        context.registerBean("preferredClientService", AnnotatedClientService.class,
                () -> new AnnotatedClientService("preferred-" + name));
    }

    private static final class StubContextRefresher extends ContextRefresher {

        private final Map<String, Object> updatedProperties;

        private StubContextRefresher(GenericApplicationContext context, RefreshScope scope,
                Map<String, Object> updatedProperties) {
            super(context, scope);
            this.updatedProperties = updatedProperties;
        }

        @Override
        protected void updateEnvironment() {
            getContext().getEnvironment().getPropertySources()
                    .addFirst(new MapPropertySource("refreshed", this.updatedProperties));
        }
    }

    private static final class TestNamedContextFactory extends NamedContextFactory<TestSpecification> {

        private TestNamedContextFactory(
                Map<String, ApplicationContextInitializer<GenericApplicationContext>> initializers) {
            super(DefaultNamedContextConfiguration.class, "testClients", "client.name", initializers);
        }
    }

    private static final class TestSpecification implements Specification {

        private final String name;

        private final Class<?>[] configuration;

        private TestSpecification(String name, Class<?>... configuration) {
            this.name = name;
            this.configuration = Arrays.copyOf(configuration, configuration.length);
        }

        @Override
        public String getName() {
            return this.name;
        }

        @Override
        public Class<?>[] getConfiguration() {
            return Arrays.copyOf(this.configuration, this.configuration.length);
        }
    }

    private static final class DefaultNamedContextConfiguration {
    }

    private record ClientSettings(String name) {
    }

    private static class ClientService {

        private final String description;

        private ClientService(String description) {
            this.description = description;
        }

        private String description() {
            return this.description;
        }
    }

    @PreferredClient
    private static final class AnnotatedClientService extends ClientService {

        private AnnotatedClientService(String description) {
            super(description);
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    private @interface PreferredClient {
    }
}
