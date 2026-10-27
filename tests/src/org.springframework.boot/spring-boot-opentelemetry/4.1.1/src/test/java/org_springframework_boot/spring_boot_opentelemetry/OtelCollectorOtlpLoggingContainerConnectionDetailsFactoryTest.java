/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_opentelemetry;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;

import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.opentelemetry.autoconfigure.logging.otlp.OtlpLoggingConnectionDetails;
import org.springframework.boot.opentelemetry.autoconfigure.logging.otlp.Transport;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.testcontainers.service.connection.ServiceConnectionAutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

public class OtelCollectorOtlpLoggingContainerConnectionDetailsFactoryTest {

    @Test
    void serviceConnectionProvidesTransportSpecificCollectorUrls() {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(CollectorConfiguration.class)) {
            OtlpLoggingConnectionDetails connectionDetails = context.getBean(OtlpLoggingConnectionDetails.class);

            assertThat(connectionDetails.getUrl(Transport.HTTP)).isEqualTo("http://collector.test:14318/v1/logs");
            assertThat(connectionDetails.getUrl(Transport.GRPC)).isEqualTo("http://collector.test:14317/v1/logs");
            assertThat(connectionDetails.getSslBundle()).isNull();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration(ServiceConnectionAutoConfiguration.class)
    static class CollectorConfiguration {

        @Bean(destroyMethod = "")
        @ServiceConnection(name = "otel/opentelemetry-collector-contrib")
        RecordingCollectorContainer collectorContainer() {
            return new RecordingCollectorContainer();
        }

    }

    static final class RecordingCollectorContainer extends GenericContainer<RecordingCollectorContainer> {

        private boolean running;

        RecordingCollectorContainer() {
            super("otel/opentelemetry-collector-contrib:latest");
        }

        @Override
        public String getDockerImageName() {
            return "otel/opentelemetry-collector-contrib:latest";
        }

        @Override
        public void start() {
            this.running = true;
        }

        @Override
        public void stop() {
            this.running = false;
        }

        @Override
        public boolean isRunning() {
            return this.running;
        }

        @Override
        public String getHost() {
            return "collector.test";
        }

        @Override
        public Integer getMappedPort(int originalPort) {
            return originalPort + 10000;
        }

    }

}
