/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import kafka.server.KafkaConfig;
import kafka.server.KafkaServer;
import kafka.utils.TestUtils;
import kafka.zk.EmbeddedZookeeper;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.CreateTopicsResult;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.LongDeserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.common.utils.Time;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Produced;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KafkaStreamsTest {

    private static final Logger logger = LoggerFactory.getLogger("KafkaStreamsTest");

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
    void testBasicKafkaStreamsProcessor() throws Exception {
        Properties properties = new Properties();
        properties.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, "PLAINTEXT://" + KAFKA_SERVER);
        try (Admin admin = Admin.create(properties)) {
            NewTopic newTopic = new NewTopic("user-regions", 1, (short) 1);
            CreateTopicsResult result = admin.createTopics(Collections.singleton(newTopic));
            result.values().get("user-regions").get();

            NewTopic newTopic1 = new NewTopic("large-regions", 1, (short) 1);
            CreateTopicsResult result1 = admin.createTopics(Collections.singleton(newTopic1));
            result1.values().get("large-regions").get();
        }

        final Properties streamsConfiguration = new Properties();
        // Give the Streams application a unique name.  The name must be unique in the Kafka cluster
        // against which the application is run.
        streamsConfiguration.put(StreamsConfig.APPLICATION_ID_CONFIG, "user-region-lambda-example");
        streamsConfiguration.put(StreamsConfig.CLIENT_ID_CONFIG, "user-region-lambda-example-client");
        // Where to find Kafka broker(s).
        streamsConfiguration.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_SERVER);
        // Specify default (de)serializers for record keys and for record values.
        streamsConfiguration.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        streamsConfiguration.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        // Records should be flushed every second. This is less than the default
        // in order to keep this example interactive.
        streamsConfiguration.put(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, 1 * 1000);

        final Serde<String> stringSerde = Serdes.String();
        final Serde<Long> longSerde = Serdes.Long();

        final StreamsBuilder builder = new StreamsBuilder();

        final KTable<String, String> userRegions = builder.table("user-regions");

        // Aggregate the user counts of by region
        final KTable<String, Long> regionCounts = userRegions
                // Count by region;
                .groupBy((userId, region) -> KeyValue.pair(region, region))
                .count();

        final KStream<String, Long> regionCountsForConsole = regionCounts
                .toStream()
                .filter((regionName, count) -> count != null);

        // write to the result topic, we need to override the value serializer to for type long
        regionCountsForConsole.to("large-regions", Produced.with(stringSerde, longSerde));

        final KafkaStreams streams = new KafkaStreams(builder.build(), streamsConfiguration);

        streams.cleanUp();
        streams.start();


        Map<String, Object> producerProperties = new HashMap<>();
        producerProperties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_SERVER);
        producerProperties.put(ProducerConfig.LINGER_MS_CONFIG, 50);
        producerProperties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProperties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProperties)) {
            producer.send(new ProducerRecord<>("user-regions", 0, "alice", "asia")).get();
            producer.send(new ProducerRecord<>("user-regions", 0, "bob", "europe")).get();
        }

        List<Map<String, Long>> receivedMessages = new ArrayList<>();

        Map<String, Object> consumerProperties = new HashMap<>();
        consumerProperties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_SERVER);
        consumerProperties.put(ConsumerConfig.GROUP_ID_CONFIG, "consumer");
        consumerProperties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        consumerProperties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProperties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, LongDeserializer.class);
        consumerProperties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (KafkaConsumer<String, Long> consumer = new KafkaConsumer<>(consumerProperties)) {
            consumer.subscribe(List.of("large-regions"));
            long end  = System.currentTimeMillis() + 30000L;
            while (receivedMessages.size() < 2 && System.currentTimeMillis() < end) {
                ConsumerRecords<String, Long> records = consumer.poll(Duration.ofMillis(100));
                for (ConsumerRecord<String, Long> record : records) {
                    receivedMessages.add(Map.of(record.key(), record.value()));
                }
            }
        }
        List<Map<String, Long>> expectedContent = List.of(Map.of("asia", 1L), Map.of("europe", 1L));
        assertThat(receivedMessages)
                .hasSize(2)
                .containsExactly(expectedContent.get(0), expectedContent.get(1));
    }
}
