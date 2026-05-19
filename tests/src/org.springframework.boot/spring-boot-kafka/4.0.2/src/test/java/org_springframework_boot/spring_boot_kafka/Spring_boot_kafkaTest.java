/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_kafka;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.boot.kafka.autoconfigure.KafkaConnectionDetails;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.retrytopic.RetryTopicConfiguration;
import org.springframework.kafka.transaction.KafkaTransactionManager;

import static org.assertj.core.api.Assertions.assertThat;

public class Spring_boot_kafkaTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(KafkaAutoConfiguration.class));

    @Test
    void autoConfigurationIsAdvertisedForSpringBootDiscovery() {
        ImportCandidates candidates = ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader());

        assertThat(candidates.getCandidates()).contains(KafkaAutoConfiguration.class.getName());
    }

    @Test
    void kafkaConnectionDetailsConfigurationExposesSuppliedClientSettings() {
        List<String> bootstrapServers = List.of("broker-a:9092", "broker-b:9092");
        KafkaConnectionDetails.Configuration configuration = KafkaConnectionDetails.Configuration
                .of(bootstrapServers, null, "SASL_SSL");

        assertThat(configuration.getBootstrapServers()).containsExactlyElementsOf(bootstrapServers);
        assertThat(configuration.getSslBundle()).isNull();
        assertThat(configuration.getSecurityProtocol()).isEqualTo("SASL_SSL");
    }

    @Test
    void kafkaPropertiesBuildSeparateClientConfigurationMaps() {
        KafkaProperties properties = new KafkaProperties();
        properties.setBootstrapServers(List.of("common:9092"));
        properties.setClientId("common-client");
        properties.getConsumer().setGroupId("orders");
        properties.getConsumer().setEnableAutoCommit(false);
        properties.getConsumer().setKeyDeserializer(StringDeserializer.class);
        properties.getConsumer().getProperties().put("max.partition.fetch.bytes", "4096");
        properties.getProducer().setAcks("all");
        properties.getProducer().setRetries(2);
        properties.getProducer().setKeySerializer(StringSerializer.class);
        properties.getProducer().getProperties().put("linger.ms", "5");
        properties.getAdmin().setClientId("admin-client");
        properties.getAdmin().getProperties().put("request.timeout.ms", "2000");
        properties.getStreams().setApplicationId("stream-app");
        properties.getStreams().getProperties().put("num.stream.threads", "2");

        Map<String, Object> consumerProperties = properties.buildConsumerProperties();
        Map<String, Object> producerProperties = properties.buildProducerProperties();
        Map<String, Object> adminProperties = properties.buildAdminProperties();
        Map<String, Object> streamsProperties = properties.buildStreamsProperties();

        assertThat(consumerProperties).containsEntry(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, List.of("common:9092"))
                .containsEntry(ConsumerConfig.CLIENT_ID_CONFIG, "common-client")
                .containsEntry(ConsumerConfig.GROUP_ID_CONFIG, "orders")
                .containsEntry(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false)
                .containsEntry(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class)
                .containsEntry("max.partition.fetch.bytes", "4096");
        assertThat(producerProperties).containsEntry(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, List.of("common:9092"))
                .containsEntry(ProducerConfig.CLIENT_ID_CONFIG, "common-client")
                .containsEntry(ProducerConfig.ACKS_CONFIG, "all")
                .containsEntry(ProducerConfig.RETRIES_CONFIG, 2)
                .containsEntry(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class)
                .containsEntry("linger.ms", "5");
        assertThat(adminProperties).containsEntry(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, List.of("common:9092"))
                .containsEntry(AdminClientConfig.CLIENT_ID_CONFIG, "admin-client")
                .containsEntry("request.timeout.ms", "2000");
        assertThat(streamsProperties).containsEntry("bootstrap.servers", List.of("common:9092"))
                .containsEntry("client.id", "common-client")
                .containsEntry("application.id", "stream-app")
                .containsEntry("num.stream.threads", "2");
    }

    @Test
    void autoConfigurationBindsPropertiesAndCreatesKafkaInfrastructureBeans() {
        this.contextRunner
                .withPropertyValues("spring.kafka.bootstrap-servers=localhost:9092",
                        "spring.kafka.consumer.group-id=orders",
                        "spring.kafka.consumer.auto-offset-reset=earliest",
                        "spring.kafka.consumer.key-deserializer="
                                + "org.apache.kafka.common.serialization.StringDeserializer",
                        "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
                        "spring.kafka.producer.transaction-id-prefix=tx-",
                        "spring.kafka.template.default-topic=orders",
                        "spring.kafka.template.transaction-id-prefix=template-tx-",
                        "spring.kafka.admin.auto-create=false",
                        "spring.kafka.admin.fail-fast=true",
                        "spring.kafka.listener.ack-mode=manual",
                        "spring.kafka.listener.concurrency=3",
                        "spring.kafka.listener.poll-timeout=125ms",
                        "spring.kafka.listener.missing-topics-fatal=false",
                        "spring.kafka.listener.auto-startup=false",
                        "spring.kafka.retry.topic.enabled=true",
                        "spring.kafka.retry.topic.attempts=4")
                .run((context) -> {
                    assertThat(context).hasSingleBean(KafkaProperties.class);
                    assertThat(context).hasSingleBean(KafkaConnectionDetails.class);
                    assertThat(context).hasSingleBean(DefaultKafkaConsumerFactory.class);
                    assertThat(context).hasSingleBean(DefaultKafkaProducerFactory.class);
                    assertThat(context).hasSingleBean(KafkaTemplate.class);
                    assertThat(context).hasSingleBean(KafkaAdmin.class);
                    assertThat(context).hasSingleBean(KafkaTransactionManager.class);
                    assertThat(context).hasSingleBean(ConcurrentKafkaListenerContainerFactory.class);
                    assertThat(context).hasSingleBean(RetryTopicConfiguration.class);

                    KafkaProperties properties = context.getBean(KafkaProperties.class);
                    assertThat(properties.getConsumer().getGroupId()).isEqualTo("orders");
                    assertThat(properties.getListener().getPollTimeout()).isEqualTo(Duration.ofMillis(125));
                    assertThat(properties.getRetry().getTopic().getAttempts()).isEqualTo(4);

                    DefaultKafkaConsumerFactory<?, ?> consumerFactory = context
                            .getBean(DefaultKafkaConsumerFactory.class);
                    DefaultKafkaProducerFactory<?, ?> producerFactory = context
                            .getBean(DefaultKafkaProducerFactory.class);
                    KafkaTemplate<?, ?> kafkaTemplate = context.getBean(KafkaTemplate.class);
                    KafkaAdmin kafkaAdmin = context.getBean(KafkaAdmin.class);

                    assertThat(consumerFactory.getConfigurationProperties())
                            .containsEntry(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, List.of("localhost:9092"))
                            .containsEntry(ConsumerConfig.GROUP_ID_CONFIG, "orders")
                            .containsEntry(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
                            .containsEntry(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
                    assertThat(producerFactory.getConfigurationProperties())
                            .containsEntry(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, List.of("localhost:9092"))
                            .containsEntry(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
                    assertThat(producerFactory.getTransactionIdPrefix()).isEqualTo("tx-");
                    assertThat(kafkaTemplate.getDefaultTopic()).isEqualTo("orders");
                    assertThat(kafkaTemplate.getTransactionIdPrefix()).isEqualTo("template-tx-");
                    assertThat(kafkaAdmin.getConfigurationProperties())
                            .containsEntry(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, List.of("localhost:9092"));

                    assertListenerContainerProperties(context.getBean(ConcurrentKafkaListenerContainerFactory.class));
                });
    }

    @Test
    void customConnectionDetailsOverridePropertyBasedBootstrapServersForClientFactories() {
        this.contextRunner
                .withBean(KafkaConnectionDetails.class, TestKafkaConnectionDetails::new)
                .withPropertyValues("spring.kafka.bootstrap-servers=ignored:9092",
                        "spring.kafka.admin.auto-create=false")
                .run((context) -> {
                    DefaultKafkaConsumerFactory<?, ?> consumerFactory = context
                            .getBean(DefaultKafkaConsumerFactory.class);
                    DefaultKafkaProducerFactory<?, ?> producerFactory = context
                            .getBean(DefaultKafkaProducerFactory.class);
                    KafkaAdmin kafkaAdmin = context.getBean(KafkaAdmin.class);

                    assertThat(consumerFactory.getConfigurationProperties())
                            .containsEntry(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, List.of("consumer:9092"))
                            .containsEntry(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_PLAINTEXT");
                    assertThat(producerFactory.getConfigurationProperties())
                            .containsEntry(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, List.of("producer:9092"))
                            .containsEntry(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SSL");
                    assertThat(kafkaAdmin.getConfigurationProperties())
                            .containsEntry(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, List.of("admin:9092"))
                            .containsEntry(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "PLAINTEXT");
                });
    }

    @SuppressWarnings("unchecked")
    private void assertListenerContainerProperties(ConcurrentKafkaListenerContainerFactory<?, ?> rawFactory) {
        ConcurrentKafkaListenerContainerFactory<Object, Object> factory =
                (ConcurrentKafkaListenerContainerFactory<Object, Object>) rawFactory;
        ConcurrentMessageListenerContainer<Object, Object> container = factory.createContainer("orders");
        try {
            ContainerProperties containerProperties = container.getContainerProperties();

            assertThat(container.getConcurrency()).isEqualTo(3);
            assertThat(containerProperties.getAckMode()).isSameAs(ContainerProperties.AckMode.MANUAL);
            assertThat(containerProperties.getPollTimeout()).isEqualTo(125L);
            assertThat(containerProperties.isMissingTopicsFatal()).isFalse();
            assertThat(container.isAutoStartup()).isFalse();
        } finally {
            container.stop();
        }
    }

    private static final class TestKafkaConnectionDetails implements KafkaConnectionDetails {

        @Override
        public List<String> getBootstrapServers() {
            return List.of("default:9092");
        }

        @Override
        public String getSecurityProtocol() {
            return "SASL_PLAINTEXT";
        }

        @Override
        public KafkaConnectionDetails.Configuration getConsumer() {
            return KafkaConnectionDetails.Configuration.of(List.of("consumer:9092"), null, "SASL_PLAINTEXT");
        }

        @Override
        public KafkaConnectionDetails.Configuration getProducer() {
            return KafkaConnectionDetails.Configuration.of(List.of("producer:9092"), null, "SSL");
        }

        @Override
        public KafkaConnectionDetails.Configuration getAdmin() {
            return KafkaConnectionDetails.Configuration.of(List.of("admin:9092"), null, "PLAINTEXT");
        }

    }

}
