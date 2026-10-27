/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_testcontainers;

import java.security.KeyStore;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.lifecycle.Startable;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisConnectionDetails;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.testcontainers.beans.TestcontainerBeanDefinition;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.boot.testcontainers.lifecycle.TestcontainersLifecycleApplicationContextInitializer;
import org.springframework.boot.testcontainers.lifecycle.TestcontainersStartup;
import org.springframework.boot.testcontainers.service.connection.PemTrustStore;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.testcontainers.service.connection.ServiceConnectionAutoConfiguration;
import org.springframework.boot.testcontainers.service.connection.Ssl;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

public class Spring_boot_testcontainersTest {

    private static final String TEST_CERTIFICATE = """
            -----BEGIN CERTIFICATE-----
            MIIDCTCCAfGgAwIBAgIUakBJBWl5YwlpzHNTfsAVlmbrhOgwDQYJKoZIhvcNAQEL
            BQAwFDESMBAGA1UEAwwJbG9jYWxob3N0MB4XDTI2MDkwNTExNDAxOFoXDTM2MDkw
            MjExNDAxOFowFDESMBAGA1UEAwwJbG9jYWxob3N0MIIBIjANBgkqhkiG9w0BAQEF
            AAOCAQ8AMIIBCgKCAQEAmP44J23L6kRbyQ7AfZUxA1CYr90ayj2MDQKShaYw1iMs
            YUy90OwWrnBSsF+0v6sbob/Xx6nbYHuap3W00oZ3M3mHZFrLXcKsBvFA9/rOr/N7
            uS2BUP7YyM5LLBpNjNaZQrJH6c4LpKr5wAuFopCwRV3aCnFLTB8ORvWZpvyZZTtN
            5UYWClIelhYnyelZhGdV1JNMu1vkEh6GDPguuyGv5QWYM/LeT/C/eqapKe9Ho0UU
            M3kO0rE0aCtKFchfKjzm22AkueYhEpr4K0qjllcP+Buf/0Y8/INoKrQP4JGXsV01
            qdY5BLtilndE/k42N+HJo0v3HDUydges+ktD6nLHawIDAQABo1MwUTAdBgNVHQ4E
            FgQUleaC02Y9BeHnyK0ibFwQDOJVs28wHwYDVR0jBBgwFoAUleaC02Y9BeHnyK0i
            bFwQDOJVs28wDwYDVR0TAQH/BAUwAwEB/zANBgkqhkiG9w0BAQsFAAOCAQEAXSTY
            jHGwLfNqv+S8Ic5N+MF0X10ygARu1Srb6o7HyJwuggWNefF+vUjzPNa6ZIfpTKM4
            MCuEFXZdp6jA5ONAeL7hPRDvyWIED3VWot6o+9iWtbJzySKYbOUpCc57gVt8ev35
            L3pdcCwNowfKdulgFp0nPdChR6OaeoMhdddDXMuYC4/J/USqTzui/jxvT17XRzbs
            A6AX+v6jBMBu/QA27XyG4uRCYZTc8McsTI9tlAh3k+fMSP2OC8pUzBDK30vxAPht
            5uN7gMEqZOBfr2/vJSag88pdlpwqsLrZR0iItrE1rAIgONdeCXCQW/E6s2AqFJ8c
            rExAjDayNCHR+utzqw==
            -----END CERTIFICATE-----
            """;

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

    @Test
    void sslServiceConnectionCreatesBundleFromPemTrustMaterial() throws Exception {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(SslServiceConnectionConfiguration.class)) {
            DataRedisConnectionDetails connectionDetails = context.getBean(DataRedisConnectionDetails.class);
            SslBundle sslBundle = connectionDetails.getSslBundle();

            assertThat(sslBundle).isNotNull();
            assertThat(sslBundle.getProtocol()).isEqualTo("TLSv1.3");
            assertThat(sslBundle.getOptions().getCiphers()).containsExactly("TLS_AES_128_GCM_SHA256");
            assertThat(sslBundle.getOptions().getEnabledProtocols()).containsExactly("TLSv1.3");
            assertThat(sslBundle.getKey().getPassword()).isEqualTo("key-password");
            assertThat(sslBundle.getKey().getAlias()).isEqualTo("client");
            assertThat(sslBundle.getStores().getKeyStore()).isNull();
            KeyStore trustStore = sslBundle.getStores().getTrustStore();
            assertThat(trustStore).isNotNull();
            assertThat(trustStore.size()).isEqualTo(1);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration(ServiceConnectionAutoConfiguration.class)
    static class SslServiceConnectionConfiguration {

        @Bean(destroyMethod = "")
        @ServiceConnection(name = "redis")
        @PemTrustStore(certificate = TEST_CERTIFICATE)
        @Ssl(
                protocol = "TLSv1.3",
                ciphers = "TLS_AES_128_GCM_SHA256",
                enabledProtocols = "TLSv1.3",
                keyPassword = "key-password",
                keyAlias = "client")
        RedisContainer redisContainer() {
            return new RedisContainer("redis:latest");
        }

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

}
