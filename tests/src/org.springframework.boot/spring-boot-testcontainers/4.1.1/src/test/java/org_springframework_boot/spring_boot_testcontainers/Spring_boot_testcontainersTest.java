/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_testcontainers;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.lifecycle.Startable;
import org.testcontainers.postgresql.PostgreSQLContainer;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.boot.testcontainers.beans.TestcontainerBeanDefinition;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.boot.testcontainers.lifecycle.TestcontainersLifecycleApplicationContextInitializer;
import org.springframework.boot.testcontainers.lifecycle.TestcontainersStartup;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.testcontainers.service.connection.ServiceConnectionAutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

public class Spring_boot_testcontainersTest {

    @Test
    void importTestcontainersRegistersContainerFieldAsInfrastructureBeanWithoutStartingIt() {
        RecordingContainer container = ImportedContainerDefinitions.CONTAINER;

        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(ImportedContainerConfiguration.class)) {
            Map<String, RecordingContainer> containers = context.getBeansOfType(RecordingContainer.class);

            assertThat(containers).hasSize(1).containsValue(container);
            BeanDefinition beanDefinition = context.getBeanDefinition(containers.keySet().iterator().next());
            assertThat(beanDefinition).isInstanceOf(TestcontainerBeanDefinition.class);
            assertThat(beanDefinition.getRole()).isEqualTo(BeanDefinition.ROLE_INFRASTRUCTURE);
            assertThat(((TestcontainerBeanDefinition) beanDefinition).getContainerImageName())
                    .isEqualTo("postgres:18-alpine");
            assertThat(container.getStartCount()).isZero();
        }
    }

    @Test
    void importTestcontainersPublishesDynamicPropertiesAndStartsTheirContainerOnDemand() {
        RecordingContainer container = DynamicPropertyContainerDefinitions.CONTAINER;

        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(DynamicPropertyContainerConfiguration.class)) {
            assertThat(container.getStartCount()).isZero();

            assertThat(context.getEnvironment().getProperty("sample.container.state"))
                    .isEqualTo("running-1");
            assertThat(container.isRunning()).isTrue();
            assertThat(container.getStartCount()).isEqualTo(1);
        }

        assertThat(container.isRunning()).isFalse();
        assertThat(container.getStopCount()).isEqualTo(1);
    }

    @Test
    void lifecycleInitializerStartsAndStopsAllStartableBeansOnlyOnce() {
        RecordingStartable first = new RecordingStartable();
        RecordingStartable second = new RecordingStartable();
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment()
                .getPropertySources()
                .addFirst(new MapPropertySource("testcontainers", Map.of(TestcontainersStartup.PROPERTY, "parallel")));
        TestcontainersLifecycleApplicationContextInitializer initializer =
                new TestcontainersLifecycleApplicationContextInitializer();
        initializer.initialize(context);
        initializer.initialize(context);
        context.registerBean("firstStartable", RecordingStartable.class, () -> first);
        context.registerBean("secondStartable", RecordingStartable.class, () -> second);

        try {
            context.refresh();

            assertThat(first.isRunning()).isTrue();
            assertThat(second.isRunning()).isTrue();
            assertThat(first.getStartCount()).isEqualTo(1);
            assertThat(second.getStartCount()).isEqualTo(1);
        } finally {
            context.close();
        }

        assertThat(first.isRunning()).isFalse();
        assertThat(second.isRunning()).isFalse();
        assertThat(first.getStopCount()).isEqualTo(1);
        assertThat(second.getStopCount()).isEqualTo(1);
    }

    @Configuration(proxyBeanMethods = false)
    @ImportTestcontainers(ImportedContainerDefinitions.class)
    static class ImportedContainerConfiguration {

    }

    static class ImportedContainerDefinitions {

        static final RecordingContainer CONTAINER = new RecordingContainer();

    }

    @Configuration(proxyBeanMethods = false)
    @ImportTestcontainers(DynamicPropertyContainerDefinitions.class)
    static class DynamicPropertyContainerConfiguration {

    }

    static class DynamicPropertyContainerDefinitions {

        static final RecordingContainer CONTAINER = new RecordingContainer();

        @DynamicPropertySource
        static void containerProperties(DynamicPropertyRegistry registry) {
            registry.add("sample.container.state", CONTAINER::state);
        }

    }

    static final class RecordingContainer extends GenericContainer<RecordingContainer> {

        private final AtomicInteger startCount = new AtomicInteger();

        private final AtomicInteger stopCount = new AtomicInteger();

        private volatile boolean running;

        RecordingContainer() {
            super("postgres:18-alpine");
        }

        @Override
        public String getDockerImageName() {
            return "postgres:18-alpine";
        }

        @Override
        public void start() {
            this.startCount.incrementAndGet();
            this.running = true;
        }

        @Override
        public void stop() {
            this.stopCount.incrementAndGet();
            this.running = false;
        }

        @Override
        public boolean isRunning() {
            return this.running;
        }

        String state() {
            return "running-" + this.startCount.get();
        }

        int getStartCount() {
            return this.startCount.get();
        }

        int getStopCount() {
            return this.stopCount.get();
        }

    }

    static final class RecordingStartable implements Startable {

        private final AtomicInteger startCount = new AtomicInteger();

        private final AtomicInteger stopCount = new AtomicInteger();

        private volatile boolean running;

        @Override
        public void start() {
            this.startCount.incrementAndGet();
            this.running = true;
        }

        @Override
        public void stop() {
            this.stopCount.incrementAndGet();
            this.running = false;
        }

        boolean isRunning() {
            return this.running;
        }

        int getStartCount() {
            return this.startCount.get();
        }

        int getStopCount() {
            return this.stopCount.get();
        }

    }

    @Nested
    @Testcontainers(disabledWithoutDocker = true)
    class DockerServiceConnectionTests {

        static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine")
                .withDatabaseName("application")
                .withUsername("application")
                .withPassword("secret")
                .withStartupTimeout(Duration.ofSeconds(30))
                .withStartupTimeoutSeconds(30);

        @Test
        void serviceConnectionProvidesRunningContainerDetailsThroughApplicationContext() {
            AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
            new TestcontainersLifecycleApplicationContextInitializer().initialize(context);
            context.register(ServiceConnectionConfiguration.class);

            try {
                context.refresh();
                JdbcConnectionDetails connectionDetails = context.getBean(JdbcConnectionDetails.class);

                assertThat(POSTGRES.isRunning()).isTrue();
                assertThat(connectionDetails.getJdbcUrl()).isEqualTo(POSTGRES.getJdbcUrl());
                assertThat(connectionDetails.getUsername()).isEqualTo("application");
                assertThat(connectionDetails.getPassword()).isEqualTo("secret");
                assertThat(connectionDetails.getDriverClassName()).isEqualTo("org.postgresql.Driver");
            } finally {
                context.close();
            }

            assertThat(POSTGRES.isRunning()).isFalse();
        }

    }

    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration(ServiceConnectionAutoConfiguration.class)
    static class ServiceConnectionConfiguration {

        @Bean
        @ServiceConnection(type = JdbcConnectionDetails.class)
        PostgreSQLContainer postgresContainer() {
            return DockerServiceConnectionTests.POSTGRES;
        }

    }
}
