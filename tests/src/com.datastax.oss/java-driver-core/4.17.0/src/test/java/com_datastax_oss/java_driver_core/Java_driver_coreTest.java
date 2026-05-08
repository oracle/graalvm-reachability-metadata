/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_datastax_oss.java_driver_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.DefaultConsistencyLevel;
import com.datastax.oss.driver.api.core.ProtocolVersion;
import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import com.datastax.oss.driver.api.core.config.DriverConfig;
import com.datastax.oss.driver.api.core.config.DriverConfigLoader;
import com.datastax.oss.driver.api.core.config.DriverExecutionProfile;
import com.datastax.oss.driver.api.core.cql.BatchStatement;
import com.datastax.oss.driver.api.core.cql.DefaultBatchType;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import com.datastax.oss.driver.api.core.data.CqlDuration;
import com.datastax.oss.driver.api.core.data.TupleValue;
import com.datastax.oss.driver.api.core.type.DataTypes;
import com.datastax.oss.driver.api.core.type.TupleType;
import com.datastax.oss.driver.api.core.type.VectorType;
import com.datastax.oss.driver.api.core.type.codec.TypeCodec;
import com.datastax.oss.driver.api.core.type.codec.TypeCodecs;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class Java_driver_coreTest {
    @Test
    void cqlIdentifiersPreserveInternalNamesAndCqlRendering() {
        CqlIdentifier simple = CqlIdentifier.fromCql("account_id");
        CqlIdentifier quoted = CqlIdentifier.fromCql("\"Mixed Case\"");
        CqlIdentifier fromInternal = CqlIdentifier.fromInternal("Mixed Case");

        assertThat(simple.asInternal()).isEqualTo("account_id");
        assertThat(simple.asCql(true)).isEqualTo("account_id");
        assertThat(quoted.asInternal()).isEqualTo("Mixed Case");
        assertThat(quoted.asCql(true)).isEqualTo("\"Mixed Case\"");
        assertThat(fromInternal).isEqualTo(quoted);
        assertThat(fromInternal.hashCode()).isEqualTo(quoted.hashCode());
    }

    @Test
    void simpleStatementsExposeQueryAttributesAndImmutableCopyOperations() {
        ByteBuffer routingKey = ByteBuffer.wrap(new byte[] {1, 2, 3, 4});
        ByteBuffer pagingState = ByteBuffer.wrap(new byte[] {9, 8, 7});
        ByteBuffer customPayload = ByteBuffer.wrap("shop".getBytes(StandardCharsets.UTF_8));

        SimpleStatement statement = SimpleStatement.builder("SELECT * FROM products WHERE sku = :sku")
                .setExecutionProfileName("analytics")
                .setKeyspace("inventory")
                .addNamedValue("sku", "coffee")
                .setConsistencyLevel(DefaultConsistencyLevel.LOCAL_QUORUM)
                .setSerialConsistencyLevel(DefaultConsistencyLevel.LOCAL_SERIAL)
                .setPageSize(128)
                .setTimeout(Duration.ofMillis(1_500))
                .setIdempotence(true)
                .setTracing(true)
                .setRoutingKeyspace("inventory")
                .setRoutingKey(routingKey.duplicate())
                .setPagingState(pagingState.duplicate())
                .setQueryTimestamp(123_456_789L)
                .setNowInSeconds(42)
                .addCustomPayload("tenant", customPayload.duplicate())
                .build();

        assertThat(statement.getQuery()).isEqualTo("SELECT * FROM products WHERE sku = :sku");
        assertThat(statement.getExecutionProfileName()).isEqualTo("analytics");
        assertThat(statement.getKeyspace()).isEqualTo(CqlIdentifier.fromInternal("inventory"));
        assertThat(statement.getNamedValues()).containsEntry(CqlIdentifier.fromCql("sku"), "coffee");
        assertThat(statement.getConsistencyLevel()).isEqualTo(DefaultConsistencyLevel.LOCAL_QUORUM);
        assertThat(statement.getSerialConsistencyLevel()).isEqualTo(DefaultConsistencyLevel.LOCAL_SERIAL);
        assertThat(statement.getPageSize()).isEqualTo(128);
        assertThat(statement.getTimeout()).isEqualTo(Duration.ofMillis(1_500));
        assertThat(statement.isIdempotent()).isTrue();
        assertThat(statement.isTracing()).isTrue();
        assertThat(statement.getRoutingKeyspace()).isEqualTo(CqlIdentifier.fromInternal("inventory"));
        assertThat(statement.getRoutingKey()).isEqualTo(ByteBuffer.wrap(new byte[] {1, 2, 3, 4}));
        assertThat(statement.getPagingState()).isEqualTo(ByteBuffer.wrap(new byte[] {9, 8, 7}));
        assertThat(statement.getQueryTimestamp()).isEqualTo(123_456_789L);
        assertThat(statement.getNowInSeconds()).isEqualTo(42);
        assertThat(statement.getCustomPayload())
                .containsEntry("tenant", ByteBuffer.wrap("shop".getBytes(StandardCharsets.UTF_8)));

        SimpleStatement copy = statement
                .setQuery("SELECT sku FROM products WHERE sku = :sku")
                .setPageSize(16)
                .setIdempotent(false)
                .setTracing(false);

        assertThat(statement.getQuery()).isEqualTo("SELECT * FROM products WHERE sku = :sku");
        assertThat(statement.getPageSize()).isEqualTo(128);
        assertThat(statement.isIdempotent()).isTrue();
        assertThat(statement.isTracing()).isTrue();
        assertThat(copy.getQuery()).isEqualTo("SELECT sku FROM products WHERE sku = :sku");
        assertThat(copy.getPageSize()).isEqualTo(16);
        assertThat(copy.isIdempotent()).isFalse();
        assertThat(copy.isTracing()).isFalse();
    }

    @Test
    void batchStatementsGroupChildStatementsAndExposeBatchSpecificOperations() {
        SimpleStatement insertProduct = SimpleStatement.newInstance(
                "INSERT INTO products (sku, quantity) VALUES (?, ?)", "coffee", 10);
        SimpleStatement updateProduct = SimpleStatement.newInstance(
                "UPDATE products SET quantity = ? WHERE sku = ?", 12, "coffee");

        BatchStatement batch = BatchStatement.builder(DefaultBatchType.UNLOGGED)
                .setKeyspace("inventory")
                .addStatement(insertProduct)
                .addStatement(updateProduct)
                .build();

        assertThat(batch.getBatchType()).isEqualTo(DefaultBatchType.UNLOGGED);
        assertThat(batch.getKeyspace()).isEqualTo(CqlIdentifier.fromInternal("inventory"));
        assertThat(batch.size()).isEqualTo(2);
        assertThat(batch).containsExactly(insertProduct, updateProduct);

        BatchStatement loggedBatch = batch.setBatchType(DefaultBatchType.LOGGED);
        BatchStatement emptyBatch = batch.clear();

        assertThat(batch.getBatchType()).isEqualTo(DefaultBatchType.UNLOGGED);
        assertThat(batch.size()).isEqualTo(2);
        assertThat(loggedBatch.getBatchType()).isEqualTo(DefaultBatchType.LOGGED);
        assertThat(loggedBatch).containsExactly(insertProduct, updateProduct);
        assertThat(emptyBatch.getBatchType()).isEqualTo(DefaultBatchType.UNLOGGED);
        assertThat(emptyBatch.size()).isZero();
        assertThat(emptyBatch).isEmpty();
    }

    @Test
    void typeCodecsEncodeDecodeAndFormatScalarAndCollectionValues() {
        ByteBuffer encodedText = TypeCodecs.TEXT.encode("driver", ProtocolVersion.DEFAULT);
        ByteBuffer encodedInt = TypeCodecs.INT.encode(17, ProtocolVersion.DEFAULT);
        UUID uuid = UUID.fromString("01234567-89ab-cdef-0123-456789abcdef");
        ByteBuffer encodedUuid = TypeCodecs.UUID.encode(uuid, ProtocolVersion.DEFAULT);

        assertThat(TypeCodecs.TEXT.decode(encodedText, ProtocolVersion.DEFAULT)).isEqualTo("driver");
        assertThat(TypeCodecs.INT.decode(encodedInt, ProtocolVersion.DEFAULT)).isEqualTo(17);
        assertThat(TypeCodecs.UUID.decode(encodedUuid, ProtocolVersion.DEFAULT)).isEqualTo(uuid);
        assertThat(TypeCodecs.TEXT.parse("'driver'")).isEqualTo("driver");
        assertThat(TypeCodecs.INT.parse("17")).isEqualTo(17);
        assertThat(TypeCodecs.UUID.parse(uuid.toString())).isEqualTo(uuid);
        assertThat(TypeCodecs.TEXT.format("driver")).isEqualTo("'driver'");
        assertThat(TypeCodecs.INT.format(17)).isEqualTo("17");

        TypeCodec<List<String>> listCodec = TypeCodecs.listOf(TypeCodecs.TEXT);
        List<String> colors = Arrays.asList("red", "green", "blue");
        ByteBuffer encodedList = listCodec.encode(colors, ProtocolVersion.DEFAULT);

        TypeCodec<Set<Integer>> setCodec = TypeCodecs.setOf(TypeCodecs.INT);
        Set<Integer> numbers = new LinkedHashSet<>(Arrays.asList(3, 1, 4));
        ByteBuffer encodedSet = setCodec.encode(numbers, ProtocolVersion.DEFAULT);

        assertThat(listCodec.decode(encodedList, ProtocolVersion.DEFAULT)).containsExactly("red", "green", "blue");
        assertThat(setCodec.decode(encodedSet, ProtocolVersion.DEFAULT)).containsExactlyInAnyOrder(3, 1, 4);
        assertThat(listCodec.getCqlType()).isEqualTo(DataTypes.listOf(DataTypes.TEXT));
        assertThat(setCodec.getCqlType()).isEqualTo(DataTypes.setOf(DataTypes.INT));
    }

    @Test
    void tupleDurationAndVectorTypesExposeStructuredValues() {
        TupleType tupleType = DataTypes.tupleOf(DataTypes.INT, DataTypes.TEXT, DataTypes.BOOLEAN);
        TupleValue tuple = tupleType.newValue(7, "seven", true);
        CqlDuration duration = CqlDuration.from("1mo2d3ns");
        VectorType vectorType = DataTypes.vectorOf(DataTypes.FLOAT, 3);

        assertThat(tupleType.getComponentTypes()).containsExactly(DataTypes.INT, DataTypes.TEXT, DataTypes.BOOLEAN);
        assertThat(tuple.getInt(0)).isEqualTo(7);
        assertThat(tuple.getString(1)).isEqualTo("seven");
        assertThat(tuple.getBoolean(2)).isTrue();
        assertThat(tuple.getType()).isEqualTo(tupleType);

        assertThat(duration.getMonths()).isEqualTo(1);
        assertThat(duration.getDays()).isEqualTo(2);
        assertThat(duration.getNanoseconds()).isEqualTo(3L);
        assertThat(CqlDuration.newInstance(1, 2, 3L)).isEqualTo(duration);

        assertThat(vectorType.getDimensions()).isEqualTo(3);
        assertThat(vectorType.getElementType()).isEqualTo(DataTypes.FLOAT);
    }

    @Test
    void programmaticConfigLoaderBuildsDefaultAndNamedExecutionProfiles() {
        DriverConfigLoader loader = DriverConfigLoader.programmaticBuilder()
                .withString(DefaultDriverOption.SESSION_NAME, "driver-core-test")
                .withStringList(DefaultDriverOption.CONTACT_POINTS, Arrays.asList("127.0.0.1:9042"))
                .withDuration(DefaultDriverOption.REQUEST_TIMEOUT, Duration.ofMillis(250))
                .withInt(DefaultDriverOption.REQUEST_PAGE_SIZE, 50)
                .withString(DefaultDriverOption.REQUEST_CONSISTENCY, "LOCAL_QUORUM")
                .startProfile("slow")
                .withDuration(DefaultDriverOption.REQUEST_TIMEOUT, Duration.ofSeconds(2))
                .withInt(DefaultDriverOption.REQUEST_PAGE_SIZE, 500)
                .endProfile()
                .build();

        try {
            DriverConfig config = loader.getInitialConfig();
            DriverExecutionProfile defaultProfile = config.getDefaultProfile();
            DriverExecutionProfile slowProfile = config.getProfile("slow");

            assertThat(defaultProfile.getName()).isEqualTo(DriverExecutionProfile.DEFAULT_NAME);
            assertThat(defaultProfile.getString(DefaultDriverOption.SESSION_NAME)).isEqualTo("driver-core-test");
            assertThat(defaultProfile.getStringList(DefaultDriverOption.CONTACT_POINTS))
                    .containsExactly("127.0.0.1:9042");
            assertThat(defaultProfile.getDuration(DefaultDriverOption.REQUEST_TIMEOUT))
                    .isEqualTo(Duration.ofMillis(250));
            assertThat(defaultProfile.getInt(DefaultDriverOption.REQUEST_PAGE_SIZE)).isEqualTo(50);
            assertThat(defaultProfile.getString(DefaultDriverOption.REQUEST_CONSISTENCY)).isEqualTo("LOCAL_QUORUM");

            assertThat(slowProfile.getName()).isEqualTo("slow");
            assertThat(slowProfile.getDuration(DefaultDriverOption.REQUEST_TIMEOUT)).isEqualTo(Duration.ofSeconds(2));
            assertThat(slowProfile.getInt(DefaultDriverOption.REQUEST_PAGE_SIZE)).isEqualTo(500);
            assertThat(config.getProfiles()).containsKey("slow");
        } finally {
            loader.close();
        }
    }
}
