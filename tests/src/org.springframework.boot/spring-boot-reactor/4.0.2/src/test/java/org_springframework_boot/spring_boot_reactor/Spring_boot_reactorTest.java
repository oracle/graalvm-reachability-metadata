/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_reactor;

import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Hooks;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.LazyInitializationExcludeFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.reactor.ReactorEnvironmentPostProcessor;
import org.springframework.boot.reactor.autoconfigure.ReactorAutoConfiguration;
import org.springframework.boot.reactor.autoconfigure.ReactorProperties;
import org.springframework.boot.reactor.autoconfigure.ReactorProperties.ContextPropagationMode;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.UrlResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.core.io.support.SpringFactoriesLoader;

import static org.assertj.core.api.Assertions.assertThat;

public class Spring_boot_reactorTest {

    private static final String REACTOR_CONTEXT_PROPAGATION = "spring.reactor.context-propagation";

    private static final String VIRTUAL_THREADS_ENABLED = "spring.threads.virtual.enabled";

    private static final String BOUNDED_ELASTIC_ON_VIRTUAL_THREADS =
            "reactor.schedulers.defaultBoundedElasticOnVirtualThreads";

    @AfterEach
    void resetReactorHooks() {
        Hooks.disableAutomaticContextPropagation();
    }

    @Test
    void propertiesExposeLimitedContextPropagationByDefault() {
        ReactorProperties properties = new ReactorProperties();

        assertThat(properties.getContextPropagation()).isEqualTo(ContextPropagationMode.LIMITED);
        assertThat(ContextPropagationMode.values())
                .containsExactly(ContextPropagationMode.AUTO, ContextPropagationMode.LIMITED);
    }

    @Test
    void propertiesCanBeBoundFromSpringEnvironment() {
        StandardEnvironment environment = environmentWith(Map.of(REACTOR_CONTEXT_PROPAGATION, "auto"));

        ReactorProperties properties = Binder.get(environment)
                .bind("spring.reactor", Bindable.of(ReactorProperties.class))
                .get();

        assertThat(properties.getContextPropagation()).isEqualTo(ContextPropagationMode.AUTO);
    }

    @Test
    void springFactoriesDiscoversReactorEnvironmentPostProcessor() throws Exception {
        ClassLoader classLoader = ReactorEnvironmentPostProcessor.class.getClassLoader();

        assertThat(springFactoryResourceValues(classLoader, EnvironmentPostProcessor.class.getName()))
                .anySatisfy((factoryNames) -> assertThat(factoryNames)
                        .contains(ReactorEnvironmentPostProcessor.class.getName()));
    }

    @Test
    void autoConfigurationIsPublishedAsImportCandidate() {
        ClassLoader classLoader = ReactorAutoConfiguration.class.getClassLoader();

        assertThat(ImportCandidates.load(AutoConfiguration.class, classLoader).getCandidates())
                .contains(ReactorAutoConfiguration.class.getName());
    }

    @Test
    void environmentPostProcessorUsesLowestPrecedenceOrder() {
        ReactorEnvironmentPostProcessor postProcessor = new ReactorEnvironmentPostProcessor();

        assertThat(postProcessor.getOrder()).isEqualTo(Ordered.LOWEST_PRECEDENCE);
    }

    @Test
    void environmentPostProcessorLeavesBoundedElasticSchedulerUnchangedWhenVirtualThreadsAreDisabled() {
        withSystemPropertyCleared(BOUNDED_ELASTIC_ON_VIRTUAL_THREADS, () -> {
            StandardEnvironment environment = environmentWith(Map.of(VIRTUAL_THREADS_ENABLED, "false"));

            new ReactorEnvironmentPostProcessor().postProcessEnvironment(environment, null);

            assertThat(System.getProperty(BOUNDED_ELASTIC_ON_VIRTUAL_THREADS)).isNull();
        });
    }

    @Test
    void environmentPostProcessorEnablesBoundedElasticSchedulerVirtualThreadsWhenVirtualThreadsAreActive() {
        withSystemPropertyCleared(BOUNDED_ELASTIC_ON_VIRTUAL_THREADS, () -> {
            StandardEnvironment environment = environmentWith(Map.of(VIRTUAL_THREADS_ENABLED, "true"));

            new ReactorEnvironmentPostProcessor().postProcessEnvironment(environment, null);

            assertThat(System.getProperty(BOUNDED_ELASTIC_ON_VIRTUAL_THREADS)).isEqualTo("true");
        });
    }

    @Test
    void autoConfigurationLeavesAutomaticContextPropagationDisabledInLimitedMode() {
        Hooks.disableAutomaticContextPropagation();

        try (AnnotationConfigApplicationContext context = autoConfigurationContext(Map.of())) {
            assertThat(context.getBean(ReactorAutoConfiguration.class)).isNotNull();
            assertThat(context.getBean(ReactorProperties.class).getContextPropagation())
                    .isEqualTo(ContextPropagationMode.LIMITED);
            assertThat(Hooks.isAutomaticContextPropagationEnabled()).isFalse();
        }
    }

    @Test
    void autoConfigurationEnablesAutomaticContextPropagationInAutoMode() {
        Hooks.disableAutomaticContextPropagation();

        try (AnnotationConfigApplicationContext context = autoConfigurationContext(
                Map.of(REACTOR_CONTEXT_PROPAGATION, "auto"))) {
            assertThat(context.getBean(ReactorAutoConfiguration.class)).isNotNull();
            assertThat(context.getBean(ReactorProperties.class).getContextPropagation())
                    .isEqualTo(ContextPropagationMode.AUTO);
            assertThat(context.getBeansOfType(LazyInitializationExcludeFilter.class)).hasSize(1);
            assertThat(Hooks.isAutomaticContextPropagationEnabled()).isTrue();
        }
    }

    private static AnnotationConfigApplicationContext autoConfigurationContext(Map<String, Object> properties) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", properties));
        context.register(ReactorAutoConfiguration.class);
        context.refresh();
        return context;
    }

    private static List<String> springFactoryResourceValues(ClassLoader classLoader, String factoryTypeName)
            throws Exception {
        List<String> values = new ArrayList<>();
        Enumeration<URL> resources = classLoader.getResources(SpringFactoriesLoader.FACTORIES_RESOURCE_LOCATION);
        while (resources.hasMoreElements()) {
            Properties properties = PropertiesLoaderUtils.loadProperties(new UrlResource(resources.nextElement()));
            String value = properties.getProperty(factoryTypeName);
            if (value != null) {
                values.add(value);
            }
        }
        return values;
    }

    private static StandardEnvironment environmentWith(Map<String, Object> properties) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", properties));
        return environment;
    }

    private static void withSystemPropertyCleared(String name, Runnable action) {
        String previousValue = System.getProperty(name);
        try {
            System.clearProperty(name);
            action.run();
        }
        finally {
            if (previousValue != null) {
                System.setProperty(name, previousValue);
            }
            else {
                System.clearProperty(name);
            }
        }
    }

}
