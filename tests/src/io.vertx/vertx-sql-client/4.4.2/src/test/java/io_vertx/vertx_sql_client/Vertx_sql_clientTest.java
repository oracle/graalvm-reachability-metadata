/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_vertx.vertx_sql_client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.tracing.TracingPolicy;
import io.vertx.sqlclient.Cursor;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.PrepareOptions;
import io.vertx.sqlclient.PreparedQuery;
import io.vertx.sqlclient.PreparedStatement;
import io.vertx.sqlclient.PropertyKind;
import io.vertx.sqlclient.Query;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.RowStream;
import io.vertx.sqlclient.SqlConnectOptions;
import io.vertx.sqlclient.SqlConnection;
import io.vertx.sqlclient.Transaction;
import io.vertx.sqlclient.Tuple;
import io.vertx.sqlclient.data.NullValue;
import io.vertx.sqlclient.data.Numeric;
import io.vertx.sqlclient.spi.DatabaseMetadata;
import io.vertx.sqlclient.spi.Driver;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.ServiceConfigurationError;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(10)
public class Vertx_sql_clientTest {
    private enum SampleStatus {
        NEW,
        DONE
    }

    @Test
    void tupleSupportsFactoryMethodsMutationAndTypeDiscovery() {
        List<Object> source = new ArrayList<>(List.of("first", 2));
        Tuple copied = Tuple.from(source);
        Tuple wrapped = Tuple.wrap(source);
        source.set(0, "changed");

        assertThat(copied.getString(0)).isEqualTo("first");
        assertThat(wrapped.getString(0)).isEqualTo("changed");

        Object[] array = {"alpha", 1};
        Tuple copiedArray = Tuple.from(array);
        Tuple wrappedArray = Tuple.wrap(array);
        array[0] = "beta";

        assertThat(copiedArray.getString(0)).isEqualTo("alpha");
        assertThat(wrappedArray.getString(0)).isEqualTo("beta");

        Tuple tuple = Tuple.tuple()
                .addString("name")
                .addInteger(42)
                .addBoolean(null)
                .addArrayOfString(new String[] {"a", "b"});

        assertThat(tuple.size()).isEqualTo(4);
        assertThat(tuple.getValue(2)).isNull();
        assertThat(tuple.types()).containsExactly(String.class, Integer.class, Boolean.class, String[].class);
        assertThat(tuple.deepToString())
                .startsWith("[name,42,null,[Ljava.lang.String;@")
                .endsWith("]");

        tuple.clear();
        assertThat(tuple.size()).isZero();

        assertThat(Tuple.of("a", "b", "c", "d", "e", "f", "g").size()).isEqualTo(7);
        assertThat(Tuple.tuple(Arrays.asList("x", "y")).deepToString()).isEqualTo("[x,y]");
    }

    @Test
    void tupleCoercesScalarValuesThroughTypedAccessors() {
        UUID uuid = UUID.randomUUID();
        LocalDateTime dateTime = LocalDateTime.of(2024, 2, 3, 4, 5, 6);
        OffsetDateTime offsetDateTime = OffsetDateTime.of(dateTime, ZoneOffset.UTC);
        JsonObject jsonObject = new JsonObject().put("answer", 42);
        JsonArray jsonArray = new JsonArray().add("value");
        Buffer buffer = Buffer.buffer("payload");

        Tuple tuple = Tuple.of(
                7,
                SampleStatus.DONE,
                12.5D,
                Numeric.parse("123.45"),
                jsonObject,
                jsonArray,
                Tuple.JSON_NULL,
                dateTime,
                offsetDateTime,
                buffer,
                uuid.toString(),
                99L);

        assertThat(tuple.getShort(0)).isEqualTo((short) 7);
        assertThat(tuple.getInteger(1)).isEqualTo(1);
        assertThat(tuple.getLong(0)).isEqualTo(7L);
        assertThat(tuple.getFloat(2)).isEqualTo(12.5F);
        assertThat(tuple.getDouble(1)).isEqualTo(1D);
        assertThat(tuple.getString(1)).isEqualTo("DONE");
        assertThat(tuple.getNumeric(3).bigDecimalValue()).isEqualByComparingTo("123.45");
        assertThat(tuple.getJsonObject(4).getInteger("answer")).isEqualTo(42);
        assertThat(tuple.getJsonArray(5).getString(0)).isEqualTo("value");
        assertThat(tuple.getJson(6)).isSameAs(Tuple.JSON_NULL);
        assertThat(tuple.getTemporal(7)).isEqualTo(dateTime);
        assertThat(tuple.getLocalDate(8)).isEqualTo(LocalDate.of(2024, 2, 3));
        assertThat(tuple.getLocalTime(8)).isEqualTo(LocalTime.of(4, 5, 6));
        assertThat(tuple.getLocalDateTime(8)).isEqualTo(dateTime);
        assertThat(tuple.getOffsetTime(8)).isEqualTo(OffsetTime.of(4, 5, 6, 0, ZoneOffset.UTC));
        assertThat(tuple.getOffsetDateTime(8)).isEqualTo(offsetDateTime);
        assertThat(tuple.getBuffer(9).toString()).isEqualTo("payload");
        assertThat(tuple.getUUID(10)).isEqualTo(uuid);
        assertThat(tuple.getBigDecimal(11)).isEqualByComparingTo(BigDecimal.valueOf(99L));
        assertThat(tuple.get(String.class, 10)).isEqualTo(uuid.toString());
        assertThat(tuple.get(Integer.class, 10)).isNull();

        assertThatExceptionOfType(ClassCastException.class).isThrownBy(() -> Tuple.of(new Object()).getJson(0));
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> tuple.get(null, 0));
    }

    @Test
    void tupleCoercesArrayValuesThroughTypedAccessors() {
        Number[] numbers = {1, 2L, 3.5D};
        LocalDateTime[] dateTimes = {LocalDateTime.of(2024, 1, 2, 3, 4)};
        OffsetDateTime[] offsetDateTimes = {OffsetDateTime.of(2024, 5, 6, 7, 8, 9, 0, ZoneOffset.UTC)};
        UUID[] uuids = {UUID.randomUUID()};
        BigDecimal[] decimals = {new BigDecimal("10.25")};
        Buffer[] buffers = {Buffer.buffer("a")};
        JsonObject jsonObject = new JsonObject().put("k", "v");
        JsonArray jsonArray = new JsonArray().add(1);

        Tuple tuple = Tuple.tuple()
                .addValue(numbers)
                .addValue(new SampleStatus[] {SampleStatus.NEW, SampleStatus.DONE})
                .addValue(new Object[] {jsonObject})
                .addValue(new Object[] {jsonArray})
                .addValue(new Object[] {Tuple.JSON_NULL, "text", 4, true, jsonObject, jsonArray})
                .addValue(dateTimes)
                .addValue(offsetDateTimes)
                .addArrayOfBuffer(buffers)
                .addArrayOfUUID(uuids)
                .addArrayOfBigDecimal(decimals);

        assertThat(tuple.getArrayOfShorts(0)).containsExactly((short) 1, (short) 2, (short) 3);
        assertThat(tuple.getArrayOfIntegers(0)).containsExactly(1, 2, 3);
        assertThat(tuple.getArrayOfLongs(0)).containsExactly(1L, 2L, 3L);
        assertThat(tuple.getArrayOfFloats(0)).containsExactly(1F, 2F, 3.5F);
        assertThat(tuple.getArrayOfDoubles(0)).containsExactly(1D, 2D, 3.5D);
        assertThat(tuple.getArrayOfNumerics(0))
                .containsExactly(Numeric.create(1), Numeric.create(2L), Numeric.create(3.5D));
        assertThat(tuple.getArrayOfStrings(1)).containsExactly("NEW", "DONE");
        assertThat(tuple.getArrayOfJsonObjects(2)).containsExactly(jsonObject);
        assertThat(tuple.getArrayOfJsonArrays(3)).containsExactly(jsonArray);
        assertThat(tuple.getArrayOfJsons(4)).containsExactly(Tuple.JSON_NULL, "text", 4, true, jsonObject, jsonArray);
        assertThat(tuple.getArrayOfTemporals(5)).containsExactly(dateTimes[0]);
        assertThat(tuple.getArrayOfLocalDates(5)).containsExactly(LocalDate.of(2024, 1, 2));
        assertThat(tuple.getArrayOfLocalTimes(5)).containsExactly(LocalTime.of(3, 4));
        assertThat(tuple.getArrayOfLocalDateTimes(5)).containsExactly(dateTimes[0]);
        assertThat(tuple.getArrayOfOffsetTimes(6)).containsExactly(OffsetTime.of(7, 8, 9, 0, ZoneOffset.UTC));
        assertThat(tuple.getArrayOfOffsetDateTimes(6)).containsExactly(offsetDateTimes[0]);
        assertThat(tuple.getArrayOfBuffers(7)).containsExactly(buffers);
        assertThat(tuple.getArrayOfUUIDs(8)).containsExactly(uuids);
        assertThat(tuple.getArrayOfBigDecimals(9)).containsExactly(decimals);

        assertThatExceptionOfType(ClassCastException.class)
                .isThrownBy(() -> Tuple.of(new Object[] {new Object()}).getArrayOfJsons(0));
    }

    @Test
    void optionsRoundTripThroughGeneratedJsonConverters() {
        PoolOptions poolOptions = new PoolOptions()
                .setMaxSize(8)
                .setMaxWaitQueueSize(16)
                .setIdleTimeout(2)
                .setIdleTimeoutUnit(TimeUnit.MINUTES)
                .setMaxLifetime(3)
                .setMaxLifetimeUnit(TimeUnit.HOURS)
                .setPoolCleanerPeriod(250)
                .setConnectionTimeout(5)
                .setConnectionTimeoutUnit(TimeUnit.MILLISECONDS)
                .setShared(true)
                .setName("integration-pool")
                .setEventLoopSize(2);

        JsonObject poolJson = poolOptions.toJson();
        PoolOptions decodedPoolOptions = new PoolOptions(poolJson);

        assertThat(decodedPoolOptions.getMaxSize()).isEqualTo(8);
        assertThat(decodedPoolOptions.getMaxWaitQueueSize()).isEqualTo(16);
        assertThat(decodedPoolOptions.getIdleTimeout()).isEqualTo(2);
        assertThat(decodedPoolOptions.getIdleTimeoutUnit()).isEqualTo(TimeUnit.MINUTES);
        assertThat(decodedPoolOptions.getMaxLifetime()).isEqualTo(3);
        assertThat(decodedPoolOptions.getMaxLifetimeUnit()).isEqualTo(TimeUnit.HOURS);
        assertThat(decodedPoolOptions.getPoolCleanerPeriod()).isEqualTo(250);
        assertThat(decodedPoolOptions.getConnectionTimeout()).isEqualTo(5);
        assertThat(decodedPoolOptions.getConnectionTimeoutUnit()).isEqualTo(TimeUnit.MILLISECONDS);
        assertThat(decodedPoolOptions.isShared()).isTrue();
        assertThat(decodedPoolOptions.getName()).isEqualTo("integration-pool");
        assertThat(decodedPoolOptions.getEventLoopSize()).isEqualTo(2);

        SqlConnectOptions connectOptions = new SqlConnectOptions()
                .setHost("db.example.test")
                .setPort(5432)
                .setUser("user")
                .setPassword("secret")
                .setDatabase("app")
                .setCachePreparedStatements(true)
                .setPreparedStatementCacheMaxSize(32)
                .setPreparedStatementCacheSqlLimit(12)
                .addProperty("application_name", "native-test")
                .setTracingPolicy(TracingPolicy.ALWAYS);

        assertThat(connectOptions.getPreparedStatementCacheSqlFilter()
                .test("select * from table_with_long_name")).isFalse();

        JsonObject connectJson = connectOptions.toJson();
        SqlConnectOptions decodedConnectOptions = new SqlConnectOptions(connectJson);

        assertThat(decodedConnectOptions.getHost()).isEqualTo("db.example.test");
        assertThat(decodedConnectOptions.getPort()).isEqualTo(5432);
        assertThat(decodedConnectOptions.getUser()).isEqualTo("user");
        assertThat(decodedConnectOptions.getPassword()).isEqualTo("secret");
        assertThat(decodedConnectOptions.getDatabase()).isEqualTo("app");
        assertThat(decodedConnectOptions.getCachePreparedStatements()).isTrue();
        assertThat(decodedConnectOptions.getPreparedStatementCacheMaxSize()).isEqualTo(32);
        assertThat(decodedConnectOptions.getProperties()).containsEntry("application_name", "native-test");
        assertThat(decodedConnectOptions.getTracingPolicy()).isEqualTo(TracingPolicy.ALWAYS);
        assertThat(decodedConnectOptions.getPreparedStatementCacheSqlFilter().test("select 1")).isTrue();
        SqlConnectOptions limitFromJson = new SqlConnectOptions(new JsonObject()
                .put("preparedStatementCacheSqlLimit", 12));
        assertThat(limitFromJson.getPreparedStatementCacheSqlFilter()
                .test("select * from table_with_long_name")).isFalse();

        SqlConnectOptions merged = decodedConnectOptions.merge(new JsonObject()
                .put("database", "other")
                .put("port", 3306));
        assertThat(merged.getHost()).isEqualTo("db.example.test");
        assertThat(merged.getDatabase()).isEqualTo("other");
        assertThat(merged.getPort()).isEqualTo(3306);
    }

    @Test
    void valueTypesAndPropertyKindsExposeExpectedSemantics() {
        Numeric parsed = Numeric.parse("100.50");
        Numeric created = Numeric.create(new BigInteger("100"));
        Numeric nan = Numeric.parse("NaN");

        assertThat(parsed.bigDecimalValue()).isEqualByComparingTo("100.50");
        assertThat(parsed.bigIntegerValue()).isEqualTo(BigInteger.valueOf(100L));
        assertThat(created).isEqualTo(Numeric.create(100L));
        assertThat(nan.isNaN()).isTrue();
        assertThat(nan.bigDecimalValue()).isNull();
        assertThatThrownBy(() -> Numeric.create(Double.POSITIVE_INFINITY)).isInstanceOf(NumberFormatException.class);

        assertThat(NullValue.of(String.class)).isSameAs(NullValue.String);
        assertThat(NullValue.of(LocalDateTime.class)).isSameAs(NullValue.LocalDateTime);
        assertThat(NullValue.of(LocalDateTime[].class)).isSameAs(NullValue.ArrayOfLocalDateTime);
        assertThat(NullValue.of(StringBuilder.class).type()).isEqualTo(StringBuilder.class);

        PropertyKind<Integer> updateCount = PropertyKind.create("update-count", Integer.class);
        PropertyKind<Integer> sameName = PropertyKind.create("update-count", Integer.class);
        PropertyKind<String> other = PropertyKind.create("other", String.class);

        assertThat(updateCount.name()).isEqualTo("update-count");
        assertThat(updateCount.type()).isEqualTo(Integer.class);
        assertThat(updateCount).isEqualTo(sameName).isNotEqualTo(other);
        assertThat(updateCount.toString()).contains("update-count", Integer.class.getName());
    }

    @Test
    void prepareOptionsDefensivelyCopiesJsonConfiguration() {
        JsonObject source = new JsonObject().put("fetch", 50);
        PrepareOptions options = new PrepareOptions(source);
        source.put("fetch", 100);

        assertThat(options.toJson().getInteger("fetch")).isEqualTo(50);

        JsonObject exported = options.toJson();
        exported.put("fetch", 200);
        assertThat(options.toJson().getInteger("fetch")).isEqualTo(50);

        PrepareOptions copy = new PrepareOptions(options);
        assertThat(copy.toJson().getInteger("fetch")).isEqualTo(50);
        assertThat(new PrepareOptions().toJson()).isEmpty();
        assertThat(options.toString()).contains("fetch");
    }

    @Test
    void rowDefaultColumnAccessorsDelegateToTupleAccessors() {
        UUID uuid = UUID.randomUUID();
        OffsetDateTime moment = OffsetDateTime.of(2024, 1, 2, 3, 4, 5, 0, ZoneOffset.UTC);
        TestRow row = new TestRow(
                List.of("flag", "count", "label", "json", "created", "id", "amount"),
                List.of(
                        true,
                        7L,
                        SampleStatus.DONE,
                        new JsonObject().put("ok", true),
                        moment,
                        uuid.toString(),
                        12.75D));

        assertThat(row.getColumnName(2)).isEqualTo("label");
        assertThat(row.getColumnIndex("created")).isEqualTo(4);
        assertThat(row.getBoolean("flag")).isTrue();
        assertThat(row.getInteger("count")).isEqualTo(7);
        assertThat(row.getString("label")).isEqualTo("DONE");
        assertThat(row.getJsonObject("json").getBoolean("ok")).isTrue();
        assertThat(row.getLocalDate("created")).isEqualTo(LocalDate.of(2024, 1, 2));
        assertThat(row.getUUID("id")).isEqualTo(uuid);
        assertThat(row.getBigDecimal("amount")).isEqualByComparingTo("12.75");

        assertThatExceptionOfType(NoSuchElementException.class).isThrownBy(() -> row.getValue("missing"));
    }

    @Test
    void driverBackedFactoriesReportMissingDriversWhenOnlyCoreClientApiIsPresent() {
        SqlConnectOptions options = new SqlConnectOptions().setHost("localhost").setPort(1234);

        assertThatExceptionOfType(ServiceConfigurationError.class)
                .isThrownBy(() -> SqlConnectOptions.fromUri("postgresql://localhost/test"))
                .withMessageContaining(Driver.class.getName());
        assertThatExceptionOfType(ServiceConfigurationError.class)
                .isThrownBy(() -> Pool.pool(options, new PoolOptions()))
                .withMessageContaining(Driver.class.getName());
    }

    @Test
    void poolWithConnectionClosesBorrowedConnectionAfterSuccessAndFailure() {
        TestSqlConnection successfulConnection = new TestSqlConnection();
        Future<String> success = new TestPool(successfulConnection)
                .withConnection(connection -> {
                    assertThat(connection).isSameAs(successfulConnection);
                    return Future.succeededFuture("done");
                });

        assertThat(success.succeeded()).isTrue();
        assertThat(success.result()).isEqualTo("done");
        assertThat(successfulConnection.closeCount).isEqualTo(1);

        TestSqlConnection failedConnection = new TestSqlConnection();
        RuntimeException failure = new RuntimeException("boom");
        Future<String> failed = new TestPool(failedConnection)
                .withConnection(connection -> Future.failedFuture(failure));

        assertThat(failed.failed()).isTrue();
        assertThat(failed.cause()).isSameAs(failure);
        assertThat(failedConnection.closeCount).isEqualTo(1);
    }

    @Test
    void preparedStatementConvenienceMethodsUseEmptyTupleArguments() {
        TestPreparedStatement preparedStatement = new TestPreparedStatement();

        Cursor cursor = preparedStatement.cursor();
        assertThat(cursor).isSameAs(preparedStatement.cursor);
        assertThat(preparedStatement.cursorArguments.size()).isZero();

        RowStream<Row> stream = preparedStatement.createStream(25);
        assertThat(stream).isSameAs(preparedStatement.stream);
        assertThat(preparedStatement.fetch).isEqualTo(25);
        assertThat(preparedStatement.streamArguments.size()).isZero();
    }

    private static final class TestPool implements Pool {
        private final SqlConnection connection;

        private TestPool(SqlConnection connection) {
            this.connection = connection;
        }

        @Override
        public void getConnection(Handler<AsyncResult<SqlConnection>> handler) {
            handler.handle(Future.succeededFuture(connection));
        }

        @Override
        public Future<SqlConnection> getConnection() {
            return Future.succeededFuture(connection);
        }

        @Override
        public Query<RowSet<Row>> query(String sql) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PreparedQuery<RowSet<Row>> preparedQuery(String sql) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PreparedQuery<RowSet<Row>> preparedQuery(String sql, PrepareOptions options) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void close(Handler<AsyncResult<Void>> handler) {
            handler.handle(Future.succeededFuture());
        }

        @Override
        public Future<Void> close() {
            return Future.succeededFuture();
        }

        @Override
        public Pool connectHandler(Handler<SqlConnection> handler) {
            handler.handle(connection);
            return this;
        }

        @Override
        public Pool connectionProvider(Function<Context, Future<SqlConnection>> provider) {
            return this;
        }

        @Override
        public int size() {
            return 1;
        }
    }

    private static final class TestSqlConnection implements SqlConnection {
        private int closeCount;

        @Override
        public Query<RowSet<Row>> query(String sql) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PreparedQuery<RowSet<Row>> preparedQuery(String sql) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PreparedQuery<RowSet<Row>> preparedQuery(String sql, PrepareOptions options) {
            throw new UnsupportedOperationException();
        }

        @Override
        public SqlConnection prepare(String sql, Handler<AsyncResult<PreparedStatement>> handler) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Future<PreparedStatement> prepare(String sql) {
            throw new UnsupportedOperationException();
        }

        @Override
        public SqlConnection prepare(
                String sql, PrepareOptions options, Handler<AsyncResult<PreparedStatement>> handler) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Future<PreparedStatement> prepare(String sql, PrepareOptions options) {
            throw new UnsupportedOperationException();
        }

        @Override
        public SqlConnection exceptionHandler(Handler<Throwable> handler) {
            return this;
        }

        @Override
        public SqlConnection closeHandler(Handler<Void> handler) {
            return this;
        }

        @Override
        public void begin(Handler<AsyncResult<Transaction>> handler) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Future<Transaction> begin() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Transaction transaction() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isSSL() {
            return false;
        }

        @Override
        public void close(Handler<AsyncResult<Void>> handler) {
            closeCount++;
            handler.handle(Future.succeededFuture());
        }

        @Override
        public Future<Void> close() {
            closeCount++;
            return Future.succeededFuture();
        }

        @Override
        public DatabaseMetadata databaseMetadata() {
            throw new UnsupportedOperationException();
        }
    }

    private static final class TestPreparedStatement implements PreparedStatement {
        private final Cursor cursor = new TestCursor();
        private final RowStream<Row> stream = new TestRowStream();
        private Tuple cursorArguments;
        private Tuple streamArguments;
        private int fetch;

        @Override
        public PreparedQuery<RowSet<Row>> query() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Cursor cursor(Tuple arguments) {
            cursorArguments = arguments;
            return cursor;
        }

        @Override
        public RowStream<Row> createStream(int fetch, Tuple arguments) {
            this.fetch = fetch;
            streamArguments = arguments;
            return stream;
        }

        @Override
        public Future<Void> close() {
            return Future.succeededFuture();
        }

        @Override
        public void close(Handler<AsyncResult<Void>> handler) {
            handler.handle(Future.succeededFuture());
        }
    }

    private static final class TestCursor implements Cursor {
        @Override
        public void read(int count, Handler<AsyncResult<RowSet<Row>>> handler) {
            handler.handle(Future.succeededFuture());
        }

        @Override
        public Future<RowSet<Row>> read(int count) {
            return Future.succeededFuture();
        }

        @Override
        public boolean hasMore() {
            return false;
        }

        @Override
        public Future<Void> close() {
            return Future.succeededFuture();
        }

        @Override
        public void close(Handler<AsyncResult<Void>> handler) {
            handler.handle(Future.succeededFuture());
        }

        @Override
        public boolean isClosed() {
            return true;
        }
    }

    private static final class TestRowStream implements RowStream<Row> {
        @Override
        public RowStream<Row> exceptionHandler(Handler<Throwable> handler) {
            return this;
        }

        @Override
        public RowStream<Row> handler(Handler<Row> handler) {
            return this;
        }

        @Override
        public RowStream<Row> pause() {
            return this;
        }

        @Override
        public RowStream<Row> resume() {
            return this;
        }

        @Override
        public RowStream<Row> endHandler(Handler<Void> handler) {
            return this;
        }

        @Override
        public RowStream<Row> fetch(long amount) {
            return this;
        }

        @Override
        public Future<Void> close() {
            return Future.succeededFuture();
        }

        @Override
        public void close(Handler<AsyncResult<Void>> handler) {
            handler.handle(Future.succeededFuture());
        }
    }

    private static final class TestRow implements Row {
        private final List<String> columns;
        private final List<Object> values;

        private TestRow(List<String> columns, List<Object> values) {
            this.columns = new ArrayList<>(columns);
            this.values = new ArrayList<>(values);
        }

        @Override
        public String getColumnName(int pos) {
            return columns.get(pos);
        }

        @Override
        public int getColumnIndex(String column) {
            return columns.indexOf(column);
        }

        @Override
        public Object getValue(int pos) {
            return values.get(pos);
        }

        @Override
        public Tuple addValue(Object value) {
            values.add(value);
            return this;
        }

        @Override
        public int size() {
            return values.size();
        }

        @Override
        public void clear() {
            values.clear();
        }

        @Override
        public List<Class<?>> types() {
            List<Class<?>> types = new ArrayList<>(values.size());
            for (Object value : values) {
                types.add(value == null ? Object.class : value.getClass());
            }
            return types;
        }
    }
}
