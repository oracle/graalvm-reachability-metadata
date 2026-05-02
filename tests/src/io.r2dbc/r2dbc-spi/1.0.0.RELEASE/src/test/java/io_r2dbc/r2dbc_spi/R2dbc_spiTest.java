/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_r2dbc.r2dbc_spi;

import io.r2dbc.spi.Blob;
import io.r2dbc.spi.Clob;
import io.r2dbc.spi.ColumnMetadata;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactoryOptions;
import io.r2dbc.spi.IsolationLevel;
import io.r2dbc.spi.NoSuchOptionException;
import io.r2dbc.spi.Nullability;
import io.r2dbc.spi.Option;
import io.r2dbc.spi.Parameter;
import io.r2dbc.spi.Parameters;
import io.r2dbc.spi.R2dbcBadGrammarException;
import io.r2dbc.spi.R2dbcTimeoutException;
import io.r2dbc.spi.R2dbcTransientException;
import io.r2dbc.spi.R2dbcType;
import io.r2dbc.spi.Result;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import io.r2dbc.spi.TransactionDefinition;
import io.r2dbc.spi.Type;
import io.r2dbc.spi.ValidationDepth;
import io.r2dbc.spi.Wrapped;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class R2dbc_spiTest {

    @Test
    void connectionFactoryOptionsBuilderMutationFilteringAndRedactionWork() {
        Option<String> applicationName = Option.valueOf("applicationName");

        ConnectionFactoryOptions original = ConnectionFactoryOptions.builder()
                .option(ConnectionFactoryOptions.DRIVER, "pool")
                .option(ConnectionFactoryOptions.PROTOCOL, "postgresql")
                .option(ConnectionFactoryOptions.HOST, "primary.internal")
                .option(ConnectionFactoryOptions.PORT, 5432)
                .option(ConnectionFactoryOptions.USER, "forge")
                .option(ConnectionFactoryOptions.PASSWORD, "s3cr3t")
                .option(applicationName, "metadata-forge")
                .build();

        assertThat(original.getRequiredValue(ConnectionFactoryOptions.HOST)).isEqualTo("primary.internal");
        assertThat(original.hasOption(ConnectionFactoryOptions.DATABASE)).isFalse();
        assertThatThrownBy(() -> original.getRequiredValue(ConnectionFactoryOptions.DATABASE))
                .isInstanceOfSatisfying(NoSuchOptionException.class, exception -> {
                    assertThat(exception.getOption()).isEqualTo(ConnectionFactoryOptions.DATABASE);
                    assertThat(exception).hasMessageContaining("database");
                });
        assertThat(original.toString())
                .contains("host=primary.internal", "user=forge", "applicationName=metadata-forge")
                .contains("password=REDACTED")
                .doesNotContain("s3cr3t");

        ConnectionFactoryOptions replica = original.mutate()
                .option(ConnectionFactoryOptions.HOST, "replica.internal")
                .build();

        assertThat(replica.getValue(ConnectionFactoryOptions.HOST)).isEqualTo("replica.internal");
        assertThat(original.getValue(ConnectionFactoryOptions.HOST)).isEqualTo("primary.internal");

        ConnectionFactoryOptions withoutSensitiveOptions = ConnectionFactoryOptions.builder()
                .from(original, option -> !option.equals(ConnectionFactoryOptions.PASSWORD))
                .build();

        assertThat(withoutSensitiveOptions.hasOption(ConnectionFactoryOptions.PASSWORD)).isFalse();
        assertThat(withoutSensitiveOptions.getValue(applicationName)).isEqualTo("metadata-forge");
    }

    @Test
    void parseConnectionUrlDecodesStructuredOptionsAndRejectsReservedQueryOptions() {
        ConnectionFactoryOptions options = ConnectionFactoryOptions.parse(
                "r2dbcs:pool:postgresql://user%20name:p%40ss+word@localhost:5432/sample%20db"
                        + "?schema=tenant%2Bone&applicationName=my+app");

        assertThat(options.getValue(ConnectionFactoryOptions.DRIVER)).isEqualTo("pool");
        assertThat(options.getValue(ConnectionFactoryOptions.PROTOCOL)).isEqualTo("postgresql");
        assertThat(options.getValue(ConnectionFactoryOptions.SSL)).isEqualTo(Boolean.TRUE);
        assertThat(options.getValue(ConnectionFactoryOptions.HOST)).isEqualTo("localhost");
        assertThat(options.getValue(ConnectionFactoryOptions.PORT)).isEqualTo(5432);
        assertThat(options.getValue(ConnectionFactoryOptions.DATABASE)).isEqualTo("sample db");
        assertThat(options.getValue(ConnectionFactoryOptions.USER)).isEqualTo("user name");
        assertThat(options.getValue(ConnectionFactoryOptions.PASSWORD)).hasToString("p@ss word");
        assertThat(options.getValue(Option.valueOf("schema"))).isEqualTo("tenant+one");
        assertThat(options.getValue(Option.valueOf("applicationName"))).isEqualTo("my app");

        assertThatThrownBy(() -> ConnectionFactoryOptions.parse("r2dbc:postgresql://localhost/test?user=alice"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not declare option user");
    }

    @Test
    void optionsAndIsolationLevelsPreserveNamesSensitivityAndTransactionAttributes() {
        Option<String> tenant = Option.valueOf("tenant");
        Option<String> sameTenant = Option.valueOf("tenant");
        Option<String> tenantDeclaredSensitiveLater = Option.sensitiveValueOf("tenant");
        Option<String> sensitiveApiKey = Option.sensitiveValueOf("apiKey");
        Option<String> sameSensitiveApiKey = Option.valueOf("apiKey");

        assertThat(tenant).isEqualTo(sameTenant);
        assertThat(tenant.hashCode()).isEqualTo(sameTenant.hashCode());
        assertThat(tenant.cast("blue")).isEqualTo("blue");
        assertThat(tenant.cast(null)).isNull();
        assertThat(tenant.toString()).contains("tenant", "sensitive=false");
        assertThat(tenantDeclaredSensitiveLater).isEqualTo(tenant);
        assertThat(tenantDeclaredSensitiveLater.toString()).contains("tenant", "sensitive=false");
        assertThat(sensitiveApiKey).isEqualTo(sameSensitiveApiKey);
        assertThat(sensitiveApiKey.toString()).contains("apiKey", "sensitive=true");

        IsolationLevel isolationLevel = IsolationLevel.valueOf("READ COMMITTED");

        assertThat(isolationLevel).isEqualTo(IsolationLevel.READ_COMMITTED);
        assertThat(isolationLevel.asSql()).isEqualTo("READ COMMITTED");
        assertThat(isolationLevel.getAttribute(TransactionDefinition.ISOLATION_LEVEL)).isSameAs(isolationLevel);
        assertThat(isolationLevel.getAttribute(TransactionDefinition.NAME)).isNull();
        assertThat(isolationLevel.toString()).contains("READ COMMITTED");
    }

    @Test
    void parametersRepresentDirectionsAndR2dbcTypesExposeMappedJavaTypes() {
        Parameter in = Parameters.in(R2dbcType.VARCHAR, "alpha");
        Parameter sameIn = Parameters.in(R2dbcType.VARCHAR, "alpha");
        Parameter out = Parameters.out(Integer.class);
        Parameter inOut = Parameters.inOut(R2dbcType.BOOLEAN, true);
        Parameter inferred = Parameters.in(42);

        assertThat(in).isInstanceOf(Parameter.In.class).isNotInstanceOf(Parameter.Out.class);
        assertThat(in).isEqualTo(sameIn).isNotEqualTo(inOut);
        assertThat(in.getType()).isEqualTo(R2dbcType.VARCHAR);
        assertThat(in.getValue()).isEqualTo("alpha");
        assertThat(in.toString()).isEqualTo("In{VARCHAR}");

        assertThat(out).isInstanceOf(Parameter.Out.class).isNotInstanceOf(Parameter.In.class);
        assertThat(out.getValue()).isNull();
        assertThat(out.getType()).isInstanceOfSatisfying(Type.InferredType.class, type -> {
            assertThat(type.getJavaType()).isEqualTo(Integer.class);
            assertThat(type.getName()).isEqualTo("(inferred)");
            assertThat(type.toString()).isEqualTo("Inferred: java.lang.Integer");
        });
        assertThat(out.toString()).isEqualTo("Out{Inferred: java.lang.Integer}");

        assertThat(inOut).isInstanceOf(Parameter.In.class).isInstanceOf(Parameter.Out.class);
        assertThat(inOut.getValue()).isEqualTo(true);
        assertThat(inOut.toString()).isEqualTo("InOut{BOOLEAN}");

        assertThat(inferred.getValue()).isEqualTo(42);
        assertThat(inferred.getType()).isInstanceOfSatisfying(Type.InferredType.class,
                type -> assertThat(type.getJavaType()).isEqualTo(Integer.class));

        assertThat(R2dbcType.VARCHAR.getName()).isEqualTo("VARCHAR");
        assertThat(R2dbcType.BLOB.getJavaType()).isEqualTo(ByteBuffer.class);
        assertThat(R2dbcType.TIMESTAMP.getJavaType()).isEqualTo(LocalDateTime.class);
        assertThat(ValidationDepth.values()).containsExactly(ValidationDepth.LOCAL, ValidationDepth.REMOTE);
    }

    @Test
    void blobStreamsDataOnceAndRejectsReuse() {
        RecordingPublisher<ByteBuffer> source = new RecordingPublisher<>(List.of(
                StandardCharsets.UTF_8.encode("graal"),
                StandardCharsets.UTF_8.encode("vm")));
        Blob blob = Blob.from(source);

        PublisherOutcome<ByteBuffer> firstRead = await(blob.stream());

        assertThat(firstRead.completed).isTrue();
        assertThat(firstRead.error).isNull();
        assertThat(decodeUtf8(firstRead.items)).isEqualTo("graalvm");
        assertThat(source.subscriptionCount()).isEqualTo(1);

        PublisherOutcome<ByteBuffer> secondRead = await(blob.stream());

        assertThat(secondRead.completed).isFalse();
        assertThat(secondRead.items).isEmpty();
        assertThat(secondRead.error)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already consumed");
    }

    @Test
    void clobDiscardConsumesSourceAndPreventsStreamingAfterRelease() {
        RecordingPublisher<CharSequence> source = new RecordingPublisher<>(List.of("metadata", "-forge"));
        Clob clob = Clob.from(source);

        PublisherOutcome<Void> discard = await(clob.discard());

        assertThat(discard.completed).isTrue();
        assertThat(discard.error).isNull();
        assertThat(source.subscriptionCount()).isEqualTo(1);

        PublisherOutcome<CharSequence> afterDiscard = await(clob.stream());

        assertThat(afterDiscard.completed).isFalse();
        assertThat(afterDiscard.items).isEmpty();
        assertThat(afterDiscard.error)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already released");
    }

    @Test
    void connectionFactoriesRejectUnsupportedDriversAndExceptionsRetainContext() {
        ConnectionFactoryOptions options = ConnectionFactoryOptions.builder()
                .option(ConnectionFactoryOptions.DRIVER, "definitely-missing")
                .option(ConnectionFactoryOptions.HOST, "localhost")
                .option(ConnectionFactoryOptions.DATABASE, "test")
                .build();

        assertThat(ConnectionFactories.find(options)).isNull();
        assertThat(ConnectionFactories.supports(options)).isFalse();
        assertThatThrownBy(() -> ConnectionFactories.get(options))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unable to create a ConnectionFactory")
                .hasMessageContaining("definitely-missing");

        IllegalArgumentException cause = new IllegalArgumentException("boom");
        R2dbcBadGrammarException badGrammar = new R2dbcBadGrammarException(
                "Bad SQL", "42000", 7, "SELECT * FROM broken", cause);

        assertThat(badGrammar.getMessage()).isEqualTo("Bad SQL");
        assertThat(badGrammar.getSqlState()).isEqualTo("42000");
        assertThat(badGrammar.getErrorCode()).isEqualTo(7);
        assertThat(badGrammar.getOffendingSql()).isEqualTo("SELECT * FROM broken");
        assertThat(badGrammar.getCause()).isSameAs(cause);
        assertThat(badGrammar.toString()).contains("Bad SQL", "[7]", "[42000]");

        R2dbcTimeoutException timeout = new R2dbcTimeoutException("Timed out", "57014", 91);

        assertThat(timeout).isInstanceOf(R2dbcTransientException.class);
        assertThat(timeout.getSql()).isNull();
        assertThat(timeout.toString()).contains("Timed out", "[91]", "[57014]");
    }

    @Test
    void resultReadableAndMetadataDefaultMethodsDelegateToTypedAccessors() {
        TestColumnMetadata nameColumn = new TestColumnMetadata("name", R2dbcType.VARCHAR);
        TestColumnMetadata countColumn = new TestColumnMetadata("count", R2dbcType.INTEGER);
        TestRowMetadata rowMetadata = new TestRowMetadata(List.of(nameColumn, countColumn));
        TestRow row = new TestRow(
                List.of("forge", 7),
                Map.of("name", "forge", "count", 7),
                rowMetadata);
        TestResult result = new TestResult(row, rowMetadata);

        assertThat(row.get(0)).isEqualTo("forge");
        assertThat(row.get("count")).isEqualTo(7);
        assertThat(row.lastIndexedType()).isEqualTo(Object.class);
        assertThat(row.lastNamedType()).isEqualTo(Object.class);

        PublisherOutcome<String> mapped = await(result.map(readable ->
                readable.get("name", String.class) + ":" + readable.get(1, Integer.class)));

        assertThat(mapped.completed).isTrue();
        assertThat(mapped.error).isNull();
        assertThat(mapped.items).containsExactly("forge:7");
        assertThat(result.lastMappedRow()).isSameAs(row);
        assertThat(result.lastMappedMetadata()).isSameAs(rowMetadata);

        assertThat(rowMetadata.contains("name")).isTrue();
        assertThat(rowMetadata.contains("missing")).isFalse();
        assertThat(rowMetadata.getColumnMetadata("count")).isSameAs(countColumn);
        assertThat(rowMetadata.getColumnMetadata(0)).isSameAs(nameColumn);

        assertThat(nameColumn.getName()).isEqualTo("name");
        assertThat(nameColumn.getType()).isEqualTo(R2dbcType.VARCHAR);
        assertThat(nameColumn.getJavaType()).isNull();
        assertThat(nameColumn.getNativeTypeMetadata()).isNull();
        assertThat(nameColumn.getNullability()).isEqualTo(Nullability.UNKNOWN);
        assertThat(nameColumn.getPrecision()).isNull();
        assertThat(nameColumn.getScale()).isNull();
    }

    @Test
    void wrappedDefaultMethodRecursivelyUnwrapsNestedWrappers() {
        DriverSpecificHandle inner = new DriverSpecificHandle("postgresql");
        TestWrapped<DriverSpecificHandle> nested = new TestWrapped<>(inner);

        assertThat(nested.unwrap(TestWrapped.class)).isSameAs(nested);
        assertThat(nested.unwrap(DriverFacade.class)).isSameAs(inner);
        assertThat(nested.unwrap(CharSequence.class)).isNull();
        assertThatThrownBy(() -> nested.unwrap((Class<Object>) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("targetClass must not be null");
    }

    private static <T> PublisherOutcome<T> await(Publisher<T> publisher) {
        List<T> items = new ArrayList<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        AtomicReference<Boolean> completed = new AtomicReference<>(false);
        CountDownLatch finished = new CountDownLatch(1);

        publisher.subscribe(new Subscriber<>() {
            @Override
            public void onSubscribe(Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(T item) {
                items.add(item);
            }

            @Override
            public void onError(Throwable throwable) {
                error.set(throwable);
                finished.countDown();
            }

            @Override
            public void onComplete() {
                completed.set(true);
                finished.countDown();
            }
        });

        assertThat(finishedAwaited(finished)).isTrue();
        return new PublisherOutcome<>(items, error.get(), completed.get());
    }

    private static boolean finishedAwaited(CountDownLatch finished) {
        try {
            return finished.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for publisher completion", exception);
        }
    }

    private static String decodeUtf8(List<ByteBuffer> buffers) {
        StringBuilder text = new StringBuilder();
        for (ByteBuffer buffer : buffers) {
            ByteBuffer copy = buffer.asReadOnlyBuffer();
            byte[] bytes = new byte[copy.remaining()];
            copy.get(bytes);
            text.append(new String(bytes, StandardCharsets.UTF_8));
        }
        return text.toString();
    }

    private static final class TestResult implements Result {

        private final Row row;

        private final RowMetadata metadata;

        private final AtomicReference<Row> lastMappedRow = new AtomicReference<>();

        private final AtomicReference<RowMetadata> lastMappedMetadata = new AtomicReference<>();

        private TestResult(Row row, RowMetadata metadata) {
            this.row = row;
            this.metadata = metadata;
        }

        @Override
        public Publisher<Long> getRowsUpdated() {
            return new RecordingPublisher<>(List.of(1L));
        }

        @Override
        public <T> Publisher<T> map(BiFunction<Row, RowMetadata, ? extends T> mappingFunction) {
            lastMappedRow.set(row);
            lastMappedMetadata.set(metadata);
            return new RecordingPublisher<>(List.of(mappingFunction.apply(row, metadata)));
        }

        @Override
        public Result filter(Predicate<Result.Segment> filter) {
            throw new UnsupportedOperationException("Not required for this test");
        }

        @Override
        public <T> Publisher<T> flatMap(Function<Result.Segment, ? extends Publisher<? extends T>> mappingFunction) {
            throw new UnsupportedOperationException("Not required for this test");
        }

        private Row lastMappedRow() {
            return lastMappedRow.get();
        }

        private RowMetadata lastMappedMetadata() {
            return lastMappedMetadata.get();
        }
    }

    private static final class TestRow implements Row {

        private final List<Object> indexedValues;

        private final Map<String, Object> namedValues;

        private final RowMetadata metadata;

        private final AtomicReference<Class<?>> lastIndexedType = new AtomicReference<>();

        private final AtomicReference<Class<?>> lastNamedType = new AtomicReference<>();

        private TestRow(List<Object> indexedValues, Map<String, Object> namedValues, RowMetadata metadata) {
            this.indexedValues = indexedValues;
            this.namedValues = namedValues;
            this.metadata = metadata;
        }

        @Override
        public RowMetadata getMetadata() {
            return metadata;
        }

        @Override
        public <T> T get(int index, Class<T> type) {
            lastIndexedType.set(type);
            return type.cast(indexedValues.get(index));
        }

        @Override
        public <T> T get(String name, Class<T> type) {
            lastNamedType.set(type);
            return type.cast(namedValues.get(name));
        }

        private Class<?> lastIndexedType() {
            return lastIndexedType.get();
        }

        private Class<?> lastNamedType() {
            return lastNamedType.get();
        }
    }

    private static final class TestRowMetadata implements RowMetadata {

        private final List<? extends ColumnMetadata> columns;

        private final Map<String, ColumnMetadata> columnsByName;

        private TestRowMetadata(List<? extends ColumnMetadata> columns) {
            this.columns = columns;
            this.columnsByName = new LinkedHashMap<>();
            for (ColumnMetadata column : columns) {
                columnsByName.put(column.getName(), column);
            }
        }

        @Override
        public ColumnMetadata getColumnMetadata(int index) {
            return columns.get(index);
        }

        @Override
        public ColumnMetadata getColumnMetadata(String name) {
            return columnsByName.get(name);
        }

        @Override
        public List<? extends ColumnMetadata> getColumnMetadatas() {
            return columns;
        }
    }

    private static final class TestColumnMetadata implements ColumnMetadata {

        private final String name;

        private final Type type;

        private TestColumnMetadata(String name, Type type) {
            this.name = name;
            this.type = type;
        }

        @Override
        public Type getType() {
            return type;
        }

        @Override
        public String getName() {
            return name;
        }
    }

    private static final class PublisherOutcome<T> {

        private final List<T> items;

        private final Throwable error;

        private final boolean completed;

        private PublisherOutcome(List<T> items, Throwable error, boolean completed) {
            this.items = items;
            this.error = error;
            this.completed = completed;
        }
    }

    private interface DriverFacade {

        String backend();
    }

    private static final class DriverSpecificHandle implements DriverFacade, Wrapped<Object> {

        private final String backend;

        private DriverSpecificHandle(String backend) {
            this.backend = backend;
        }

        @Override
        public String backend() {
            return backend;
        }

        @Override
        public Object unwrap() {
            return backend;
        }
    }

    private static final class TestWrapped<T> implements Wrapped<T> {

        private final T delegate;

        private TestWrapped(T delegate) {
            this.delegate = delegate;
        }

        @Override
        public T unwrap() {
            return delegate;
        }
    }

    private static final class RecordingPublisher<T> implements Publisher<T> {

        private final List<T> elements;

        private final AtomicInteger subscriptions = new AtomicInteger();

        private RecordingPublisher(List<T> elements) {
            this.elements = elements;
        }

        @Override
        public void subscribe(Subscriber<? super T> subscriber) {
            subscriptions.incrementAndGet();
            subscriber.onSubscribe(new Subscription() {

                private boolean finished;

                @Override
                public void request(long requested) {
                    if (finished) {
                        return;
                    }
                    finished = true;
                    if (requested <= 0) {
                        subscriber.onError(new IllegalArgumentException("Demand must be positive"));
                        return;
                    }
                    for (T element : elements) {
                        subscriber.onNext(element);
                    }
                    subscriber.onComplete();
                }

                @Override
                public void cancel() {
                    finished = true;
                }
            });
        }

        private int subscriptionCount() {
            return subscriptions.get();
        }
    }
}
