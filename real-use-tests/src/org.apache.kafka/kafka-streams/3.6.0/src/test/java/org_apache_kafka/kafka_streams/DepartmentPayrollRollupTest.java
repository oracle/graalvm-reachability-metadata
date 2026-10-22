/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Properties;

import org.apache.kafka.common.serialization.LongDeserializer;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.TopologyTestDriver;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.Stores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises Kafka Streams regrouped KTable aggregation for department payroll totals.
 * An HR application retracts an employee's previous contribution on salary changes, department
 * transfers, and deletions, publishing and querying the current payroll rather than event sums.
 */
public class DepartmentPayrollRollupTest {

    @Test
    @Timeout(60)
    public void retractsPreviousSalariesWhenEmployeeRecordsChange(@TempDir Path stateDirectory) {
        StreamsBuilder builder = new StreamsBuilder();
        builder.table("employee-salaries", Consumed.with(Serdes.String(), Serdes.String()),
                Materialized.<String, String>as(Stores.inMemoryKeyValueStore("employees"))
                        .withKeySerde(Serdes.String()).withValueSerde(Serdes.String())
                        .withCachingDisabled())
                .groupBy((employeeId, value) -> {
                    Salary salary = Salary.parse(value);
                    return KeyValue.pair(salary.department(), salary.annualCents());
                }, Grouped.with(Serdes.String(), Serdes.Long()))
                .aggregate(() -> 0L,
                        (department, salary, total) -> total + salary,
                        (department, salary, total) -> total - salary,
                        Materialized.<String, Long>as(Stores.inMemoryKeyValueStore("department-payroll"))
                                .withKeySerde(Serdes.String()).withValueSerde(Serdes.Long())
                                .withCachingDisabled())
                .toStream()
                .to("payroll-updates", Produced.with(Serdes.String(), Serdes.Long()));

        Properties properties = new Properties();
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "department-payroll-rollup");
        properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "unused:9092");
        properties.put(StreamsConfig.STATE_DIR_CONFIG, stateDirectory.toString());
        Instant start = Instant.parse("2025-01-01T12:00:00Z");
        try (TopologyTestDriver driver = new TopologyTestDriver(builder.build(), properties, start)) {
            TestInputTopic<String, String> employees = driver.createInputTopic(
                    "employee-salaries", new StringSerializer(), new StringSerializer());
            TestOutputTopic<String, Long> updates = driver.createOutputTopic(
                    "payroll-updates", new StringDeserializer(), new LongDeserializer());
            KeyValueStore<String, Long> payroll = driver.getKeyValueStore("department-payroll");

            employees.pipeInput("alice", "engineering|12000000", start);
            employees.pipeInput("bob", "engineering|9000000", start.plusSeconds(1));
            employees.pipeInput("carol", "sales|8000000", start.plusSeconds(2));
            assertEquals(Map.of("engineering", 21000000L, "sales", 8000000L),
                    updates.readKeyValuesToMap());
            assertEquals(Long.valueOf(21000000L), payroll.get("engineering"));
            assertEquals(Long.valueOf(8000000L), payroll.get("sales"));

            employees.pipeInput("alice", "engineering|13000000", start.plusSeconds(3));
            assertEquals(Map.of("engineering", 22000000L), updates.readKeyValuesToMap());
            assertEquals(Long.valueOf(22000000L), payroll.get("engineering"));

            employees.pipeInput("bob", "sales|9500000", start.plusSeconds(4));
            assertEquals(Map.of("engineering", 13000000L, "sales", 17500000L),
                    updates.readKeyValuesToMap());
            assertEquals(Long.valueOf(13000000L), payroll.get("engineering"));
            assertEquals(Long.valueOf(17500000L), payroll.get("sales"));

            employees.pipeInput("carol", (String) null, start.plusSeconds(5));
            assertEquals(Map.of("sales", 9500000L), updates.readKeyValuesToMap());
            employees.pipeInput("alice", (String) null, start.plusSeconds(6));
            assertEquals(Map.of("engineering", 0L), updates.readKeyValuesToMap());
            assertEquals(Long.valueOf(0L), payroll.get("engineering"));
            assertEquals(Long.valueOf(9500000L), payroll.get("sales"));

            employees.pipeInput("alice", "sales|14000000", start.plusSeconds(7));
            assertEquals(Map.of("sales", 23500000L), updates.readKeyValuesToMap());
            assertEquals(Long.valueOf(23500000L), payroll.get("sales"));
            assertEquals(Long.valueOf(0L), payroll.get("engineering"));
            assertTrue(updates.isEmpty());
        }
    }

    private record Salary(String department, long annualCents) {
        private static Salary parse(String value) {
            String[] fields = value.split("\\|", -1);
            return new Salary(fields[0], Long.parseLong(fields[1]));
        }
    }
}
