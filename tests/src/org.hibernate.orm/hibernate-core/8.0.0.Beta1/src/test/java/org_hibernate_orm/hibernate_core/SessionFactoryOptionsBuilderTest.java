/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_orm.hibernate_core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import org.hibernate.Interceptor;
import org.hibernate.SessionEventListener;
import org.hibernate.SessionFactory;
import org.hibernate.StatementObserver;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.cfg.Configuration;
import org.hibernate.cfg.JdbcSettings;
import org.hibernate.cfg.MappingSettings;
import org.hibernate.cfg.QuerySettings;
import org.hibernate.cfg.SessionEventSettings;
import org.hibernate.type.SqlTypes;
import org.hibernate.type.Type;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.format.FormatMapper;
import org.hibernate.type.format.FormatMapperCreationContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SessionFactoryOptionsBuilderTest {

    private static final List<String> EVENTS = new CopyOnWriteArrayList<>();

    @BeforeEach
    void clearEvents() {
        EVENTS.clear();
    }

    @Test
    void statementObserverConfiguredAsClassObservesExecutedSql() {
        Configuration configuration = SessionFactoryTestSupport.h2Configuration(Book.class);
        configuration.getProperties().put(JdbcSettings.STATEMENT_OBSERVER, RecordingStatementObserver.class);

        try (SessionFactory sessionFactory = configuration.buildSessionFactory()) {
            sessionFactory.inTransaction(session -> session.persist(new Book(1L, "Dune")));
        }

        assertThat(EVENTS).anyMatch(sql -> sql.startsWith("sql:insert into Book"));
    }

    @Test
    void statementObserverConfiguredByNameObservesExecutedSql() {
        Configuration configuration = SessionFactoryTestSupport.h2Configuration(Book.class);
        configuration.setProperty(JdbcSettings.STATEMENT_OBSERVER, RecordingStatementObserver.class.getName());

        try (SessionFactory sessionFactory = configuration.buildSessionFactory()) {
            sessionFactory.inTransaction(session -> session.persist(new Book(1L, "Emma")));
            String title = sessionFactory.fromTransaction(session -> session.find(Book.class, 1L).getTitle());
            assertThat(title).isEqualTo("Emma");
        }

        assertThat(EVENTS).anyMatch(sql -> sql.startsWith("sql:select"));
    }

    @Test
    void sessionScopedInterceptorIsInstantiatedForEachSession() {
        Configuration configuration = SessionFactoryTestSupport.h2Configuration(Book.class);
        configuration.setProperty(SessionEventSettings.SESSION_SCOPED_INTERCEPTOR, PersistRecordingInterceptor.class);

        try (SessionFactory sessionFactory = configuration.buildSessionFactory()) {
            sessionFactory.inTransaction(session -> session.persist(new Book(1L, "Ulysses")));
            sessionFactory.inTransaction(session -> session.persist(new Book(2L, "Hamlet")));
        }

        assertThat(EVENTS).containsExactly("persist:Ulysses", "persist:Hamlet");
    }

    @Test
    void autoSessionEventListenerReceivesTransactionCompletion() {
        Configuration configuration = SessionFactoryTestSupport.h2Configuration(Book.class);
        configuration.setProperty(
                SessionEventSettings.AUTO_SESSION_EVENTS_LISTENER,
                CompletionRecordingListener.class.getName());

        try (SessionFactory sessionFactory = configuration.buildSessionFactory()) {
            sessionFactory.inTransaction(session -> session.persist(new Book(1L, "Walden")));
        }

        assertThat(EVENTS).contains("transaction-completed:true");
    }

    @Test
    void inlineMutationStrategyUpdatesJoinedSubclassRows() {
        Configuration configuration = SessionFactoryTestSupport.h2Configuration(Vehicle.class, Car.class);
        configuration.setProperty(
                QuerySettings.QUERY_MULTI_TABLE_MUTATION_STRATEGY,
                "org.hibernate.query.sqm.mutation.internal.inline.InlineMutationStrategy");

        try (SessionFactory sessionFactory = configuration.buildSessionFactory()) {
            sessionFactory.inTransaction(session -> {
                session.persist(new Car(1L, "coupe", 2));
                session.persist(new Car(2L, "sedan", 4));
            });
            int updated = sessionFactory.fromTransaction(session -> session.createMutationQuery(
                    "update Car set name = 'family' where doors = 4").executeUpdate());
            String name = sessionFactory.fromTransaction(session -> session.find(Car.class, 2L).getName());

            assertThat(updated).isEqualTo(1);
            assertThat(name).isEqualTo("family");
        }
    }

    @Test
    void temporaryTableStrategiesMutateAndInsertJoinedSubclassRows() {
        Configuration configuration = SessionFactoryTestSupport.h2Configuration(Vehicle.class, Car.class);
        configuration.setProperty(
                QuerySettings.QUERY_MULTI_TABLE_MUTATION_STRATEGY,
                "org.hibernate.query.sqm.mutation.internal.temptable.LocalTemporaryTableMutationStrategy");
        configuration.setProperty(
                QuerySettings.QUERY_MULTI_TABLE_INSERT_STRATEGY,
                "org.hibernate.query.sqm.mutation.internal.temptable.LocalTemporaryTableInsertStrategy");

        try (SessionFactory sessionFactory = configuration.buildSessionFactory()) {
            sessionFactory.inTransaction(session -> session.persist(new Car(1L, "roadster", 2)));
            int inserted = sessionFactory.fromTransaction(session -> session.createMutationQuery(
                    "insert into Car (id, name, doors) select c.id + 10, c.name || '-copy', c.doors from Car c")
                    .executeUpdate());
            int deleted = sessionFactory.fromTransaction(session -> session.createMutationQuery(
                    "delete from Car where doors = 2 and id < 10").executeUpdate());
            List<String> names = sessionFactory.fromTransaction(session -> session.createSelectionQuery(
                    "select name from Car order by id", String.class).getResultList());

            assertThat(inserted).isEqualTo(1);
            assertThat(deleted).isEqualTo(1);
            assertThat(names).containsExactly("roadster-copy");
        }
    }

    @Test
    void jsonFormatMapperWithCreationContextConstructorRoundTripsJsonAttributes() {
        assertJsonAttributeRoundTrip(ContextAwareFlatJsonFormatMapper.class, "context-aware");
    }

    @Test
    void jsonFormatMapperWithDefaultConstructorRoundTripsJsonAttributes() {
        assertJsonAttributeRoundTrip(DefaultConstructorFlatJsonFormatMapper.class, "default");
    }

    private static void assertJsonAttributeRoundTrip(Class<? extends FormatMapper> mapperClass, String mapperName) {
        Configuration configuration = SessionFactoryTestSupport.h2Configuration(Gadget.class);
        configuration.setProperty(MappingSettings.JSON_FORMAT_MAPPER, mapperClass.getName());

        try (SessionFactory sessionFactory = configuration.buildSessionFactory()) {
            Map<String, String> labels = new LinkedHashMap<>();
            labels.put("color", "red");
            labels.put("size", "large");
            sessionFactory.inTransaction(session -> session.persist(new Gadget(1L, labels)));
            Map<String, String> loaded = sessionFactory.fromTransaction(session -> session.find(Gadget.class, 1L)
                    .getLabels());

            assertThat(loaded).containsEntry("color", "red").containsEntry("size", "large");
        }
        assertThat(EVENTS).contains("json-write:" + mapperName, "json-read:" + mapperName);
    }

    public static class RecordingStatementObserver implements StatementObserver {

        @Override
        public void performingSql(String sql, int batchPosition) {
            EVENTS.add("sql:" + sql);
        }
    }

    public static class PersistRecordingInterceptor implements Interceptor {

        @Override
        public boolean onPersist(Object entity, Object id, Object[] state, String[] propertyNames, Type[] types) {
            if (entity instanceof Book book) {
                EVENTS.add("persist:" + book.getTitle());
            }
            return false;
        }
    }

    public static class CompletionRecordingListener implements SessionEventListener {

        @Override
        public void transactionCompletion(boolean successful) {
            EVENTS.add("transaction-completed:" + successful);
        }
    }

    public abstract static class FlatJsonFormatMapper implements FormatMapper {

        private static final Pattern ENTRY = Pattern.compile("\"([^\"]*)\"\\s*:\\s*\"([^\"]*)\"");

        private final String name;

        protected FlatJsonFormatMapper(String name) {
            this.name = name;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T fromString(CharSequence charSequence, JavaType<T> javaType, WrapperOptions wrapperOptions) {
            EVENTS.add("json-read:" + name);
            Map<String, String> values = new LinkedHashMap<>();
            Matcher matcher = ENTRY.matcher(charSequence);
            while (matcher.find()) {
                values.put(matcher.group(1), matcher.group(2));
            }
            return (T) values;
        }

        @Override
        public <T> String toString(T value, JavaType<T> javaType, WrapperOptions wrapperOptions) {
            EVENTS.add("json-write:" + name);
            Map<?, ?> values = (Map<?, ?>) value;
            return values.entrySet().stream()
                    .map(entry -> "\"" + entry.getKey() + "\":\"" + entry.getValue() + "\"")
                    .collect(Collectors.joining(",", "{", "}"));
        }
    }

    public static class ContextAwareFlatJsonFormatMapper extends FlatJsonFormatMapper {

        public ContextAwareFlatJsonFormatMapper(FormatMapperCreationContext creationContext) {
            super("context-aware");
            assertThat(creationContext.getBootstrapContext()).isNotNull();
        }
    }

    public static class DefaultConstructorFlatJsonFormatMapper extends FlatJsonFormatMapper {

        public DefaultConstructorFlatJsonFormatMapper() {
            super("default");
        }
    }

    @Entity(name = "Book")
    public static class Book {

        @Id
        private Long id;

        private String title;

        public Book() {
        }

        public Book(Long id, String title) {
            this.id = id;
            this.title = title;
        }

        public String getTitle() {
            return title;
        }
    }

    @Entity(name = "Vehicle")
    @Inheritance(strategy = InheritanceType.JOINED)
    public static class Vehicle {

        @Id
        private Long id;

        private String name;

        public Vehicle() {
        }

        public Vehicle(Long id, String name) {
            this.id = id;
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    @Entity(name = "Car")
    public static class Car extends Vehicle {

        private int doors;

        public Car() {
        }

        public Car(Long id, String name, int doors) {
            super(id, name);
            this.doors = doors;
        }
    }

    @Entity(name = "Gadget")
    public static class Gadget {

        @Id
        private Long id;

        @JdbcTypeCode(SqlTypes.JSON)
        private Map<String, String> labels;

        public Gadget() {
        }

        public Gadget(Long id, Map<String, String> labels) {
            this.id = id;
            this.labels = labels;
        }

        public Map<String, String> getLabels() {
            return labels;
        }
    }
}
