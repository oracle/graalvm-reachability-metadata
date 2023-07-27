/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import kafka.server.KafkaConfig;
import kafka.server.KafkaServer;
import kafka.utils.TestUtils;
import kafka.zk.EmbeddedZookeeper;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.CreateTopicsResult;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.CooperativeStickyAssignor;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.RangeAssignor;
import org.apache.kafka.clients.consumer.RoundRobinAssignor;
import org.apache.kafka.clients.consumer.StickyAssignor;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RoundRobinPartitioner;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.serialization.BooleanDeserializer;
import org.apache.kafka.common.serialization.BooleanSerializer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.ByteBufferDeserializer;
import org.apache.kafka.common.serialization.ByteBufferSerializer;
import org.apache.kafka.common.serialization.BytesDeserializer;
import org.apache.kafka.common.serialization.BytesSerializer;
import org.apache.kafka.common.serialization.DoubleDeserializer;
import org.apache.kafka.common.serialization.DoubleSerializer;
import org.apache.kafka.common.serialization.FloatDeserializer;
import org.apache.kafka.common.serialization.FloatSerializer;
import org.apache.kafka.common.serialization.IntegerDeserializer;
import org.apache.kafka.common.serialization.IntegerSerializer;
import org.apache.kafka.common.serialization.ListDeserializer;
import org.apache.kafka.common.serialization.ListSerializer;
import org.apache.kafka.common.serialization.LongDeserializer;
import org.apache.kafka.common.serialization.LongSerializer;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.ShortDeserializer;
import org.apache.kafka.common.serialization.ShortSerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.common.serialization.UUIDDeserializer;
import org.apache.kafka.common.serialization.UUIDSerializer;
import org.apache.kafka.common.serialization.VoidDeserializer;
import org.apache.kafka.common.serialization.VoidSerializer;
import org.apache.kafka.common.utils.Time;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KafkaClientsTest {

    private static final Logger logger = LoggerFactory.getLogger("KafkaClientsTest");

    private static final String KAFKA_SERVER = "localhost:9092";

    private EmbeddedZookeeper zookeeper;

    private KafkaServer kafkaServer;

    @BeforeAll
    void beforeAll() {
        zookeeper = new EmbeddedZookeeper();
        logger.info("Embedded zookeeper started");
        Properties brokerProperties = new Properties();
        brokerProperties.setProperty("zookeeper.connect", "localhost:" + zookeeper.port());
        brokerProperties.setProperty("log.dirs", TestUtils.tempDir().getPath());
        brokerProperties.setProperty("listeners", "PLAINTEXT://" + KAFKA_SERVER);
        brokerProperties.setProperty("offsets.topic.replication.factor", "1");
        kafkaServer = TestUtils.createServer(new KafkaConfig(brokerProperties), Time.SYSTEM);
        logger.info("Embedded kafka server started");
    }

    @AfterAll
    void afterAll() {
        if (kafkaServer != null) {
            kafkaServer.shutdown();
            logger.info("Embedded kafka server stopped");
        }
        if (zookeeper != null) {
            zookeeper.shutdown();
            logger.info("Embedded zookeeper stopped");
        }
    }

    @Test
    void testProduceAndConsume() throws Exception {
        Properties properties = new Properties();
        properties.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, "PLAINTEXT://" + KAFKA_SERVER);
        try (Admin admin = Admin.create(properties)) {
            NewTopic newTopic = new NewTopic("test_topic", 1, (short) 1);
            CreateTopicsResult result = admin.createTopics(Collections.singleton(newTopic));
            result.values().get("test_topic").get();
        }

        Map<String, Object> producerProperties = new HashMap<>();
        producerProperties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_SERVER);
        producerProperties.put(ProducerConfig.LINGER_MS_CONFIG, 50);
        producerProperties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, IntegerSerializer.class);
        producerProperties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        try (KafkaProducer<Integer, String> producer = new KafkaProducer<>(producerProperties)) {
            producer.send(new ProducerRecord<>("test_topic", 0, 0, "message0")).get();
            producer.send(new ProducerRecord<>("test_topic", 0, 1, "message1")).get();
        }

        List<String> receivedMessages = new ArrayList<>();

        Map<String, Object> consumerProperties = new HashMap<>();
        consumerProperties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_SERVER);
        consumerProperties.put(ConsumerConfig.GROUP_ID_CONFIG, "consumer");
        consumerProperties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        consumerProperties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, IntegerDeserializer.class);
        consumerProperties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProperties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (KafkaConsumer<Integer, String> consumer = new KafkaConsumer<>(consumerProperties)) {
            consumer.subscribe(List.of("test_topic"));
            long end  = System.currentTimeMillis() + 30000L;
            while (receivedMessages.size() < 2 && System.currentTimeMillis() < end) {
                ConsumerRecords<Integer, String> records = consumer.poll(Duration.ofMillis(100));
                for (ConsumerRecord<Integer, String> record : records) {
                    receivedMessages.add(record.value());
                }
            }
        }

        assertThat(receivedMessages)
                .hasSize(2)
                .containsExactly("message0", "message1");
    }

    @ParameterizedTest
    @ValueSource(classes = {
            BooleanSerializer.class,
            BytesSerializer.class,
            ByteArraySerializer.class,
            ByteBufferSerializer.class,
            DoubleSerializer.class,
            FloatSerializer.class,
            IntegerSerializer.class,
            LongSerializer.class,
            ShortSerializer.class,
            StringSerializer.class,
            UUIDSerializer.class,
            VoidSerializer.class})
    void testSerializers(Class valueSerializer) {
        Map<String, Object> producerProperties = new HashMap<>();
        producerProperties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_SERVER);
        producerProperties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, IntegerSerializer.class);
        producerProperties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, valueSerializer);
        try (KafkaProducer producer = new KafkaProducer<>(producerProperties)) {
            assertThat(producer).isNotNull();
        }
    }

    @ParameterizedTest
    @ValueSource(classes = {
            BooleanDeserializer.class,
            BytesDeserializer.class,
            ByteArrayDeserializer.class,
            ByteBufferDeserializer.class,
            DoubleDeserializer.class,
            FloatDeserializer.class,
            IntegerDeserializer.class,
            LongDeserializer.class,
            ShortDeserializer.class,
            StringDeserializer.class,
            UUIDDeserializer.class,
            VoidDeserializer.class})
    void testDeserializers(Class valueDeserializer) {
        Map<String, Object> consumerProperties = new HashMap<>();
        consumerProperties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_SERVER);
        consumerProperties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, IntegerDeserializer.class);
        consumerProperties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, valueDeserializer);
        try (KafkaConsumer consumer = new KafkaConsumer<>(consumerProperties)) {
            assertThat(consumer).isNotNull();
        }
    }

    @ParameterizedTest
    @ValueSource(classes = {
            Serdes.BooleanSerde.class,
            Serdes.BytesSerde.class,
            Serdes.ByteArraySerde.class,
            Serdes.ByteBufferSerde.class,
            Serdes.DoubleSerde.class,
            Serdes.FloatSerde.class,
            Serdes.IntegerSerde.class,
            Serdes.LongSerde.class,
            Serdes.ShortSerde.class,
            Serdes.StringSerde.class,
            Serdes.UUIDSerde.class,
            Serdes.VoidSerde.class})
    void testListSerializers(Class serdeInnerClass) {
        Map<String, Object> producerProperties = new HashMap<>();
        producerProperties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_SERVER);
        producerProperties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, IntegerSerializer.class);
        producerProperties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ListSerializer.class);
        producerProperties.put(CommonClientConfigs.DEFAULT_LIST_VALUE_SERDE_INNER_CLASS, serdeInnerClass);
        try (KafkaProducer producer = new KafkaProducer<>(producerProperties)) {
            assertThat(producer).isNotNull();
        }
    }

    @Test
    void testListDeserializers() {
        Map<String, Object> consumerProperties = new HashMap<>();
        consumerProperties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_SERVER);
        consumerProperties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, IntegerDeserializer.class);
        consumerProperties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ListDeserializer.class);
        consumerProperties.put(CommonClientConfigs.DEFAULT_LIST_VALUE_SERDE_TYPE_CLASS, ArrayList.class);
        consumerProperties.put(CommonClientConfigs.DEFAULT_LIST_VALUE_SERDE_INNER_CLASS, Serdes.BooleanSerde.class);
        try (KafkaConsumer consumer = new KafkaConsumer<>(consumerProperties)) {
            assertThat(consumer).isNotNull();
        }
    }

    @Test
    void testRoundRobinPartitioner() {
        Map<String, Object> producerProperties = new HashMap<>();
        producerProperties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_SERVER);
        producerProperties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, IntegerSerializer.class);
        producerProperties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProperties.put(ProducerConfig.PARTITIONER_CLASS_CONFIG, RoundRobinPartitioner.class);
        try (KafkaProducer producer = new KafkaProducer<>(producerProperties)) {
            assertThat(producer).isNotNull();
        }
    }

    @ParameterizedTest
    @ValueSource(classes = {
            RangeAssignor.class,
            RoundRobinAssignor.class,
            CooperativeStickyAssignor.class,
            StickyAssignor.class})
    void testPartitionAssignmentStrategy(Class assignor) {
        Map<String, Object> consumerProperties = new HashMap<>();
        consumerProperties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_SERVER);
        consumerProperties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, IntegerDeserializer.class);
        consumerProperties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProperties.put(ConsumerConfig.PARTITION_ASSIGNMENT_STRATEGY_CONFIG, Arrays.asList(assignor));
        try (KafkaConsumer consumer = new KafkaConsumer<>(consumerProperties)) {
            assertThat(consumer).isNotNull();
        }
    }

    @Test
    void testSaslPlain() {
        Map<String, Object> consumerProperties = new HashMap<>();
        consumerProperties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_SERVER);
        consumerProperties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, IntegerDeserializer.class);
        consumerProperties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProperties.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_PLAINTEXT");
        consumerProperties.put(SaslConfigs.SASL_MECHANISM, "PLAIN");
        consumerProperties.put(SaslConfigs.SASL_JAAS_CONFIG, "org.apache.kafka.common.security.plain.PlainLoginModule required username=admin password=admin-pass;");
        try (KafkaConsumer consumer = new KafkaConsumer<>(consumerProperties)) {
            assertThat(consumer).isNotNull();
        }
    }

}
