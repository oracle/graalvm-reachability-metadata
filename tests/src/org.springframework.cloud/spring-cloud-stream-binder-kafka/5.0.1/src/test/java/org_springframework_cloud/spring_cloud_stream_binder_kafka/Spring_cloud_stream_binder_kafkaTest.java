/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_cloud.spring_cloud_stream_binder_kafka;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.kafka.autoconfigure.KafkaConnectionDetails;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.cloud.stream.binder.ExtendedConsumerProperties;
import org.springframework.cloud.stream.binder.ExtendedProducerProperties;
import org.springframework.cloud.stream.binder.kafka.KafkaBindingRebalanceListener;
import org.springframework.cloud.stream.binder.kafka.KafkaNullConverter;
import org.springframework.cloud.stream.binder.kafka.aot.KafkaBinderRuntimeHints;
import org.springframework.cloud.stream.binder.kafka.properties.JaasLoginModuleConfiguration;
import org.springframework.cloud.stream.binder.kafka.properties.KafkaBinderConfigurationProperties;
import org.springframework.cloud.stream.binder.kafka.properties.KafkaBindingProperties;
import org.springframework.cloud.stream.binder.kafka.properties.KafkaConsumerProperties;
import org.springframework.cloud.stream.binder.kafka.properties.KafkaExtendedBindingProperties;
import org.springframework.cloud.stream.binder.kafka.properties.KafkaProducerProperties;
import org.springframework.cloud.stream.binder.kafka.properties.KafkaTopicProperties;
import org.springframework.cloud.stream.binder.kafka.provisioning.KafkaTopicProvisioner;
import org.springframework.cloud.stream.provisioning.ConsumerDestination;
import org.springframework.cloud.stream.provisioning.ProducerDestination;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.security.jaas.KafkaJaasLoginModuleInitializer;
import org.springframework.kafka.support.KafkaNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import static org.assertj.core.api.Assertions.assertThat;

public class Spring_cloud_stream_binder_kafkaTest {
    private static final SpelExpressionParser PARSER = new SpelExpressionParser();

    @Test
    void binderConfigurationMergesBootKafkaBinderAndClientSpecificProperties() {
        KafkaProperties kafkaProperties = new KafkaProperties();
        kafkaProperties.setBootstrapServers(List.of("boot-broker:9092"));
        kafkaProperties.setClientId("boot-client");
        kafkaProperties.getConsumer().setGroupId("orders-group");
        kafkaProperties.getConsumer().getProperties().put("max.partition.fetch.bytes", "2048");
        kafkaProperties.getProducer().setAcks("all");
        kafkaProperties.getProducer().getProperties().put("delivery.timeout.ms", "120000");

        KafkaBinderConfigurationProperties binderProperties = binderConfiguration(kafkaProperties);
        binderProperties.setBrokers("binder-one", "binder-two:19092");
        binderProperties.setDefaultBrokerPort("29092");
        binderProperties.setRequiredAcks("1");
        binderProperties.setHeaders("traceId", "spanId");
        binderProperties.setHealthTimeout(11);
        binderProperties.setAuthorizationExceptionRetryInterval(Duration.ofSeconds(12));
        binderProperties.setConfiguration(Map.of(CommonClientConfigs.CLIENT_DNS_LOOKUP_CONFIG, "use_all_dns_ips"));
        binderProperties.setConsumerProperties(Map.of(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, "64"));
        binderProperties.setProducerProperties(Map.of(ProducerConfig.LINGER_MS_CONFIG, "5"));

        assertThat(binderProperties.getKafkaConnectionString()).isEqualTo("binder-one:29092,binder-two:19092");
        assertThat(binderProperties.getHeaders()).containsExactly("traceId", "spanId");
        assertThat(binderProperties.getHealthTimeout()).isEqualTo(11);
        assertThat(binderProperties.getRequiredAcks()).isEqualTo("1");
        assertThat(binderProperties.getAuthorizationExceptionRetryInterval()).isEqualTo(Duration.ofSeconds(12));

        assertThat(binderProperties.mergedConsumerConfiguration())
                .containsEntry(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "binder-one:29092,binder-two:19092")
                .doesNotContainKey(ConsumerConfig.GROUP_ID_CONFIG)
                .containsEntry(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, "64")
                .containsEntry(CommonClientConfigs.CLIENT_DNS_LOOKUP_CONFIG, "use_all_dns_ips");
        assertThat(binderProperties.mergedProducerConfiguration())
                .containsEntry(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "binder-one:29092,binder-two:19092")
                .containsEntry(ProducerConfig.ACKS_CONFIG, "all")
                .containsEntry(ProducerConfig.LINGER_MS_CONFIG, "5")
                .containsEntry(CommonClientConfigs.CLIENT_DNS_LOOKUP_CONFIG, "use_all_dns_ips");
    }

    @Test
    void bindingPropertiesBindNestedConsumerProducerTopicAndMapSettings() {
        Map<String, Object> source = Map.ofEntries(
                Map.entry("kafka.bindings.orders-in.consumer.enable-dlq", "true"),
                Map.entry("kafka.bindings.orders-in.consumer.dlq-name", "orders.dlq"),
                Map.entry("kafka.bindings.orders-in.consumer.dlq-partitions", "3"),
                Map.entry("kafka.bindings.orders-in.consumer.start-offset", "earliest"),
                Map.entry("kafka.bindings.orders-in.consumer.ack-mode", "manual"),
                Map.entry("kafka.bindings.orders-in.consumer.standard-headers", "both"),
                Map.entry("kafka.bindings.orders-in.consumer.topic.properties[retention.ms]", "60000"),
                Map.entry("kafka.bindings.orders-out.producer.compression-type", "gzip"),
                Map.entry("kafka.bindings.orders-out.producer.sync", "true"),
                Map.entry("kafka.bindings.orders-out.producer.close-timeout", "13"),
                Map.entry("kafka.bindings.orders-out.producer.header-patterns[0]", "!id"),
                Map.entry("kafka.bindings.orders-out.producer.header-patterns[1]", "trace-*"),
                Map.entry("kafka.bindings.orders-out.producer.topic.replication-factor", "2"),
                Map.entry("kafka.bindings.orders-out.producer.topic.properties[cleanup.policy]", "compact"));
        Binder binder = new Binder(new MapConfigurationPropertySource(source));

        KafkaExtendedBindingProperties bindings = binder.bindOrCreate("kafka", KafkaExtendedBindingProperties.class);
        KafkaConsumerProperties consumer = bindings.getExtendedConsumerProperties("orders-in");
        KafkaProducerProperties producer = bindings.getExtendedProducerProperties("orders-out");

        assertThat(bindings.getDefaultsPrefix()).isEqualTo("spring.cloud.stream.kafka.default");
        assertThat(bindings.getExtendedPropertiesEntryClass()).isEqualTo(KafkaBindingProperties.class);
        assertThat(consumer.isEnableDlq()).isTrue();
        assertThat(consumer.getDlqName()).isEqualTo("orders.dlq");
        assertThat(consumer.getDlqPartitions()).isEqualTo(3);
        assertThat(consumer.getStartOffset()).isEqualTo(KafkaConsumerProperties.StartOffset.earliest);
        assertThat(consumer.getAckMode()).isEqualTo(ContainerProperties.AckMode.MANUAL);
        assertThat(consumer.getStandardHeaders()).isEqualTo(KafkaConsumerProperties.StandardHeaders.both);
        assertThat(consumer.getTopic().getProperties()).containsEntry("retention.ms", "60000");
        assertThat(producer.getCompressionType()).isEqualTo(KafkaProducerProperties.CompressionType.gzip);
        assertThat(producer.isSync()).isTrue();
        assertThat(producer.getCloseTimeout()).isEqualTo(13);
        assertThat(producer.getHeaderPatterns()).containsExactly("!id", "trace-*");
        assertThat(producer.getTopic().getReplicationFactor()).isEqualTo((short) 2);
        assertThat(producer.getTopic().getProperties()).containsEntry("cleanup.policy", "compact");
    }

    @Test
    void producerConsumerTopicTransactionAndJaasPropertiesRetainConfiguredValues() {
        KafkaTopicProperties topic = new KafkaTopicProperties();
        topic.setReplicationFactor((short) 2);
        topic.setReplicasAssignments(Map.of(0, List.of(1, 2), 1, List.of(2, 3)));
        topic.setProperties(Map.of("min.insync.replicas", "2"));

        KafkaProducerProperties producer = new KafkaProducerProperties();
        Expression messageKeyExpression = PARSER.parseExpression("headers['partitionKey']");
        producer.setMessageKeyExpression(messageKeyExpression);
        producer.setSendTimeoutExpression(PARSER.parseExpression("10000"));
        producer.setTopic(topic);
        producer.setUseTopicHeader(true);
        producer.setRecordMetadataChannel("metadataChannel");
        producer.setTransactionManager("txManager");
        producer.setAllowNonTransactional(true);

        KafkaConsumerProperties consumer = new KafkaConsumerProperties();
        consumer.setTopic(topic);
        consumer.setTrustedPackages(new String[] {"example.orders", "example.shared"});
        consumer.setConfiguration(Map.of(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "10"));
        consumer.setPollTimeout(10_000L);
        consumer.setIdleEventInterval(10_000L);
        consumer.setCommonErrorHandlerBeanName("errorHandler");
        consumer.setDlqProducerProperties(producer);

        KafkaProperties kafkaProperties = new KafkaProperties();
        KafkaBinderConfigurationProperties binderProperties = binderConfiguration(kafkaProperties);
        binderProperties.getTransaction().setTransactionIdPrefix("tx-orders-");
        binderProperties.getTransaction().getProducer().setPartitionCount(4);
        binderProperties.getTransaction().getProducer().setMessageKeyExpression(messageKeyExpression);
        binderProperties.getMetrics().setDefaultOffsetLagMetricsEnabled(false);
        binderProperties.getMetrics().setOffsetLagMetricsInterval(Duration.ofSeconds(30));
        JaasLoginModuleConfiguration jaas = new JaasLoginModuleConfiguration();
        jaas.setLoginModule("com.sun.security.auth.module.Krb5LoginModule");
        jaas.setControlFlag("required");
        jaas.setOptions(Map.of("useKeyTab", "true"));
        binderProperties.setJaas(jaas);

        Message<String> message = MessageBuilder.withPayload("payload").setHeader("partitionKey", "order-42").build();
        assertThat(producer.getMessageKeyExpression().getValue(message)).isEqualTo("order-42");
        assertThat(producer.getTheMessageKeyExpression()).contains("partitionKey");
        assertThat(producer.getSendTimeoutExpression().getValue()).isEqualTo(10_000);
        assertThat(producer.getTopic().getReplicasAssignments()).containsEntry(0, List.of(1, 2));
        assertThat(consumer.getTrustedPackages()).containsExactly("example.orders", "example.shared");
        assertThat(consumer.getConfiguration()).containsEntry(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "10");
        assertThat(consumer.getDlqProducerProperties()).isSameAs(producer);
        assertThat(binderProperties.getTransaction().getTransactionIdPrefix()).isEqualTo("tx-orders-");
        assertThat(binderProperties.getTransaction().getProducer().getPartitionCount()).isEqualTo(4);
        assertThat(binderProperties.getMetrics().isDefaultOffsetLagMetricsEnabled()).isFalse();
        assertThat(binderProperties.getMetrics().getOffsetLagMetricsInterval()).isEqualTo(Duration.ofSeconds(30));
        assertThat(binderProperties.getJaas().getControlFlag())
                .isEqualTo(KafkaJaasLoginModuleInitializer.ControlFlag.REQUIRED);
        assertThat(binderProperties.getJaas().getOptions()).containsEntry("useKeyTab", "true");
    }

    @Test
    void provisionerUsesBinderConfigurationAndCanReturnDestinationsWithoutBrokerWhenAutoCreateIsDisabled() {
        KafkaProperties kafkaProperties = new KafkaProperties();
        kafkaProperties.getAdmin().getProperties().put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, "10000");
        KafkaBinderConfigurationProperties binderProperties = binderConfiguration(kafkaProperties);
        binderProperties.setBrokers("admin-broker:9092");
        binderProperties.setAutoCreateTopics(false);
        binderProperties.setConfiguration(Map.of(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "PLAINTEXT"));
        KafkaTopicProvisioner provisioner = new KafkaTopicProvisioner(binderProperties, kafkaProperties,
                adminProperties -> adminProperties.put("custom.admin.flag", "enabled"));
        provisioner.afterPropertiesSet();

        ExtendedProducerProperties<KafkaProducerProperties> producerProperties =
                new ExtendedProducerProperties<>(new KafkaProducerProperties());
        producerProperties.setPartitionCount(5);
        ProducerDestination producerDestination =
                provisioner.provisionProducerDestination("orders.created", producerProperties);

        KafkaConsumerProperties kafkaConsumerProperties = new KafkaConsumerProperties();
        kafkaConsumerProperties.setDestinationIsPattern(true);
        ExtendedConsumerProperties<KafkaConsumerProperties> consumerProperties =
                new ExtendedConsumerProperties<>(kafkaConsumerProperties);
        ConsumerDestination consumerDestination =
                provisioner.provisionConsumerDestination("orders\\..*", "orders-service", consumerProperties);

        assertThat(provisioner.getAdminClientProperties())
                .containsEntry(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, "admin-broker:9092")
                .containsEntry(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "PLAINTEXT")
                .containsEntry("custom.admin.flag", "enabled");
        assertThat(producerDestination.getName()).isEqualTo("orders.created");
        assertThat(producerDestination.getNameForPartition(3)).isEqualTo("orders.created");
        assertThat(producerDestination.toString()).contains("orders.created");
        assertThat(consumerDestination.getName()).isEqualTo("orders\\..*");
        assertThat(consumerDestination.toString()).contains("orders\\..*");
    }

    @Test
    void kafkaNullConverterAndRebalanceListenerExerciseMessagingCallbacks() {
        KafkaNullConverter converter = new KafkaNullConverter();
        Message<KafkaNull> kafkaNullMessage = MessageBuilder.withPayload(KafkaNull.INSTANCE).build();
        Message<String> regularMessage = MessageBuilder.withPayload("value").build();
        KafkaBindingRebalanceListener listener = new KafkaBindingRebalanceListener() {
        };

        listener.onPartitionsRevokedBeforeCommit("orders-in", null, List.of());
        listener.onPartitionsRevokedAfterCommit("orders-in", null, List.of());
        listener.onPartitionsAssigned("orders-in", null, List.of(), true);

        assertThat(converter.fromMessage(kafkaNullMessage, Object.class)).isSameAs(KafkaNull.INSTANCE);
        assertThat(converter.fromMessage(regularMessage, Object.class)).isNull();
    }

    @Test
    void runtimeHintsRegisterKafkaBindingPropertyTypes() {
        RuntimeHints hints = new RuntimeHints();
        new KafkaBinderRuntimeHints().registerHints(hints,
                Spring_cloud_stream_binder_kafkaTest.class.getClassLoader());

        assertThat(hints.reflection().getTypeHint(KafkaConsumerProperties.class)).isNotNull();
        assertThat(hints.reflection().getTypeHint(KafkaProducerProperties.class)).isNotNull();
        assertThat(hints.reflection().getTypeHint(KafkaExtendedBindingProperties.class)).isNotNull();
        assertThat(hints.reflection().getTypeHint(KafkaBindingProperties.class)).isNotNull();
    }

    private static KafkaBinderConfigurationProperties binderConfiguration(KafkaProperties kafkaProperties) {
        ObjectProvider<KafkaConnectionDetails> connectionDetails = new ObjectProvider<>() {
            @Override
            public KafkaConnectionDetails getIfAvailable() {
                return null;
            }
        };
        return new KafkaBinderConfigurationProperties(kafkaProperties, connectionDetails);
    }
}
