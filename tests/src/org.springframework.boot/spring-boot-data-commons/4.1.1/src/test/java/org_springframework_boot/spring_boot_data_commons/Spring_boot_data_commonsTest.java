/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_data_commons;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.boot.data.autoconfigure.metrics.DataMetricsProperties;
import org.springframework.boot.data.autoconfigure.web.DataWebProperties;
import org.springframework.boot.data.metrics.AutoTimer;
import org.springframework.boot.data.metrics.DefaultRepositoryTagsProvider;
import org.springframework.boot.data.metrics.MetricsRepositoryMethodInvocationListener;
import org.springframework.data.projection.ProjectionFactory;
import org.springframework.data.repository.core.RepositoryInformation;
import org.springframework.data.repository.core.RepositoryMetadata;
import org.springframework.data.repository.core.support.RepositoryFactorySupport;
import org.springframework.data.repository.query.QueryLookupStrategy;
import org.springframework.data.repository.query.QueryMethod;
import org.springframework.data.repository.query.RepositoryQuery;
import org.springframework.data.repository.query.ValueExpressionDelegate;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode;

public class Spring_boot_data_commonsTest {

    @Test
    void configuresDataMetricsProperties() {
        DataMetricsProperties properties = new DataMetricsProperties();

        Assertions.assertThat(properties.getRepository().getMetricName())
                .isEqualTo("spring.data.repository.invocations");
        Assertions.assertThat(properties.getRepository().getAutotime().isEnabled()).isTrue();
        Assertions.assertThat(properties.getRepository().getAutotime().isPercentilesHistogram()).isFalse();
        Assertions.assertThat(properties.getRepository().getAutotime().getPercentiles()).isNull();

        properties.getRepository().setMetricName("repository.calls");
        properties.getRepository().getAutotime().setEnabled(false);
        properties.getRepository().getAutotime().setPercentilesHistogram(true);
        properties.getRepository().getAutotime().setPercentiles(new double[] {0.5, 0.95 });

        Assertions.assertThat(properties.getRepository().getMetricName()).isEqualTo("repository.calls");
        Assertions.assertThat(properties.getRepository().getAutotime().isEnabled()).isFalse();
        Assertions.assertThat(properties.getRepository().getAutotime().isPercentilesHistogram()).isTrue();
        Assertions.assertThat(properties.getRepository().getAutotime().getPercentiles()).containsExactly(0.5, 0.95);
    }

    @Test
    void configuresDataWebProperties() {
        DataWebProperties properties = new DataWebProperties();

        Assertions.assertThat(properties.getPageable().getPageParameter()).isEqualTo("page");
        Assertions.assertThat(properties.getPageable().getSizeParameter()).isEqualTo("size");
        Assertions.assertThat(properties.getPageable().isOneIndexedParameters()).isFalse();
        Assertions.assertThat(properties.getPageable().getPrefix()).isEmpty();
        Assertions.assertThat(properties.getPageable().getQualifierDelimiter()).isEqualTo("_");
        Assertions.assertThat(properties.getPageable().getDefaultPageSize()).isEqualTo(20);
        Assertions.assertThat(properties.getPageable().getMaxPageSize()).isEqualTo(2000);
        Assertions.assertThat(properties.getPageable().getSerializationMode()).isEqualTo(PageSerializationMode.DIRECT);
        Assertions.assertThat(properties.getSort().getSortParameter()).isEqualTo("sort");

        properties.getPageable().setPageParameter("offset");
        properties.getPageable().setSizeParameter("limit");
        properties.getPageable().setOneIndexedParameters(true);
        properties.getPageable().setPrefix("orders");
        properties.getPageable().setQualifierDelimiter(".");
        properties.getPageable().setDefaultPageSize(25);
        properties.getPageable().setMaxPageSize(100);
        properties.getPageable().setSerializationMode(PageSerializationMode.VIA_DTO);
        properties.getSort().setSortParameter("order");

        Assertions.assertThat(properties.getPageable().getPageParameter()).isEqualTo("offset");
        Assertions.assertThat(properties.getPageable().getSizeParameter()).isEqualTo("limit");
        Assertions.assertThat(properties.getPageable().isOneIndexedParameters()).isTrue();
        Assertions.assertThat(properties.getPageable().getPrefix()).isEqualTo("orders");
        Assertions.assertThat(properties.getPageable().getQualifierDelimiter()).isEqualTo(".");
        Assertions.assertThat(properties.getPageable().getDefaultPageSize()).isEqualTo(25);
        Assertions.assertThat(properties.getPageable().getMaxPageSize()).isEqualTo(100);
        Assertions.assertThat(properties.getPageable().getSerializationMode()).isEqualTo(PageSerializationMode.VIA_DTO);
        Assertions.assertThat(properties.getSort().getSortParameter()).isEqualTo("order");
    }

    @Test
    void appliesAutoTimerAndRecordsRepositoryInvocationMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        Assertions.assertThat(AutoTimer.ENABLED.isEnabled()).isTrue();
        Assertions.assertThat(AutoTimer.DISABLED.isEnabled()).isFalse();

        AutoTimer.ENABLED.builder("standalone.operation").register(registry).record(Duration.ofMillis(1));
        Assertions.assertThat(registry.get("standalone.operation").timer().count()).isEqualTo(1);

        InMemoryRepositoryFactory factory = new InMemoryRepositoryFactory();
        factory.addInvocationListener(new MetricsRepositoryMethodInvocationListener(
                () -> registry, new DefaultRepositoryTagsProvider(), "repository.invocations", AutoTimer.ENABLED));
        BookRepository repository = factory.getRepository(BookRepository.class);

        repository.save(new Book(1L, "spring")).block(Duration.ofSeconds(10));

        Assertions.assertThat(registry.get("repository.invocations")
                .tag("repository", "BookRepository")
                .tag("method", "save")
                .tag("state", "SUCCESS")
                .tag("exception", "None")
                .timer()
                .count()).isEqualTo(1);
        registry.close();
    }

    @Test
    void recordsMethodSpecificRepositoryTimer() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        try {
            InMemoryRepositoryFactory factory = new InMemoryRepositoryFactory();
            factory.addInvocationListener(new MetricsRepositoryMethodInvocationListener(
                    () -> registry, new DefaultRepositoryTagsProvider(), "repository.invocations", AutoTimer.ENABLED));
            BookRepository repository = factory.getRepository(BookRepository.class);
            Book springBook = new Book(1L, "spring");
            repository.save(springBook).block(Duration.ofSeconds(10));

            Assertions.assertThat(repository.findByTitleWithTimer("spring").block(Duration.ofSeconds(10)))
                    .isSameAs(springBook);

            Assertions.assertThat(registry.get("repository.lookup")
                    .tag("operation", "lookup")
                    .tag("repository", "BookRepository")
                    .tag("method", "findByTitleWithTimer")
                    .tag("state", "SUCCESS")
                    .tag("exception", "None")
                    .timer()
                    .count()).isEqualTo(1);
        } finally {
            registry.close();
        }
    }

    @Test
    void recordsTypeSpecificRepositoryTimer() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        try {
            InMemoryRepositoryFactory factory = new InMemoryRepositoryFactory();
            factory.addInvocationListener(new MetricsRepositoryMethodInvocationListener(
                    () -> registry, new DefaultRepositoryTagsProvider(), "repository.invocations", AutoTimer.ENABLED));
            TimedBookRepository repository = factory.getRepository(TimedBookRepository.class);
            Book springBook = new Book(1L, "spring");

            Assertions.assertThat(repository.save(springBook).block(Duration.ofSeconds(10))).isSameAs(springBook);
            Assertions.assertThat(repository.findByTitle("spring").block(Duration.ofSeconds(10))).isSameAs(springBook);

            Assertions.assertThat(registry.get("repository.type")
                    .tag("operation", "type")
                    .tag("repository", "TimedBookRepository")
                    .tag("method", "findByTitle")
                    .tag("state", "SUCCESS")
                    .tag("exception", "None")
                    .timer()
                    .count()).isEqualTo(1);
        } finally {
            registry.close();
        }
    }

    @Test
    void createsReactiveRepositoryProxyAndExecutesCrudAndQueryMethods() {
        InMemoryRepositoryFactory factory = new InMemoryRepositoryFactory();
        BookRepository repository = factory.getRepository(BookRepository.class);
        Book first = new Book(1L, "spring");
        Book second = new Book(2L, "native");

        Assertions.assertThat(repository.save(first).block(Duration.ofSeconds(10))).isSameAs(first);
        Assertions.assertThat(repository.saveAll(Arrays.asList(second)).collectList().block(Duration.ofSeconds(10)))
                .containsExactly(second);
        Assertions.assertThat(repository.saveAll(Flux.just(new Book(3L, "data")))
                .collectList().block(Duration.ofSeconds(10))).containsExactly(new Book(3L, "data"));
        Assertions.assertThat(repository.findById(1L).block(Duration.ofSeconds(10))).isEqualTo(first);
        Assertions.assertThat(repository.findById(Mono.just(2L)).block(Duration.ofSeconds(10))).isEqualTo(second);
        Assertions.assertThat(repository.existsById(1L).block(Duration.ofSeconds(10))).isTrue();
        Assertions.assertThat(repository.existsById(Mono.just(9L)).block(Duration.ofSeconds(10))).isFalse();
        Assertions.assertThat(repository.findAll().collectList().block(Duration.ofSeconds(10)))
                .containsExactly(first, second, new Book(3L, "data"));
        Assertions.assertThat(repository.findAllById(Arrays.asList(3L, 1L)).collectList().block(Duration.ofSeconds(10)))
                .containsExactly(new Book(3L, "data"), first);
        Assertions.assertThat(repository.findAllById(Flux.just(2L, 1L)).collectList().block(Duration.ofSeconds(10)))
                .containsExactly(second, first);
        Assertions.assertThat(repository.count().block(Duration.ofSeconds(10))).isEqualTo(3L);
        Assertions.assertThat(repository.findByTitle("native").block(Duration.ofSeconds(10))).isEqualTo(second);

        repository.deleteById(3L).block(Duration.ofSeconds(10));
        repository.deleteById(Mono.just(2L)).block(Duration.ofSeconds(10));
        repository.delete(second).block(Duration.ofSeconds(10));
        repository.deleteAllById(Arrays.asList(1L)).block(Duration.ofSeconds(10));
        Assertions.assertThat(repository.count().block(Duration.ofSeconds(10))).isZero();

        repository.saveAll(Arrays.asList(first, second)).collectList().block(Duration.ofSeconds(10));
        repository.deleteAll(Arrays.asList(first, second)).block(Duration.ofSeconds(10));
        Assertions.assertThat(repository.count().block(Duration.ofSeconds(10))).isZero();
        repository.saveAll(Arrays.asList(first, second)).collectList().block(Duration.ofSeconds(10));
        repository.deleteAll(Flux.just(first, second)).block(Duration.ofSeconds(10));
        Assertions.assertThat(repository.count().block(Duration.ofSeconds(10))).isZero();
        repository.save(first).block(Duration.ofSeconds(10));
        repository.deleteAll().block(Duration.ofSeconds(10));
        Assertions.assertThat(repository.findAll().collectList().block(Duration.ofSeconds(10))).isEmpty();
    }

    public interface BookRepository extends ReactiveCrudRepository<Book, Long> {

        Mono<Book> findByTitle(String title);

        @Timed(value = "repository.lookup", extraTags = { "operation", "lookup" })
        Mono<Book> findByTitleWithTimer(String title);
    }

    @Timed(value = "repository.type", extraTags = { "operation", "type" })
    public interface TimedBookRepository extends ReactiveCrudRepository<Book, Long> {

        Mono<Book> findByTitle(String title);
    }

    public static final class Book {

        private final Long id;

        private final String title;

        public Book(Long id, String title) {
            this.id = id;
            this.title = title;
        }

        public Long getId() {
            return this.id;
        }

        public String getTitle() {
            return this.title;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Book book)) {
                return false;
            }
            return this.id.equals(book.id) && this.title.equals(book.title);
        }

        @Override
        public int hashCode() {
            return this.id.hashCode() * 31 + this.title.hashCode();
        }
    }

    public static final class InMemoryRepositoryFactory extends RepositoryFactorySupport {

        private final Map<Long, Book> books = new LinkedHashMap<>();

        @Override
        protected Object getTargetRepository(RepositoryInformation metadata) {
            return new InMemoryRepository(this.books);
        }

        @Override
        protected Class<?> getRepositoryBaseClass(RepositoryMetadata metadata) {
            return InMemoryRepository.class;
        }

        @Override
        protected Optional<QueryLookupStrategy> getQueryLookupStrategy(QueryLookupStrategy.Key key,
                ValueExpressionDelegate valueExpressionDelegate) {
            return Optional.of((method, metadata, factory, namedQueries) -> query(method, metadata, factory));
        }

        private RepositoryQuery query(java.lang.reflect.Method method, RepositoryMetadata metadata,
                ProjectionFactory factory) {
            QueryMethod queryMethod = new QueryMethod(method, metadata, factory);
            return new RepositoryQuery() {
                @Override
                public Object execute(Object[] parameters) {
                    String title = (String) parameters[0];
                    return Mono.justOrEmpty(books.values().stream()
                            .filter(book -> book.getTitle().equals(title))
                            .findFirst());
                }

                @Override
                public QueryMethod getQueryMethod() {
                    return queryMethod;
                }
            };
        }
    }

    public static final class InMemoryRepository implements ReactiveCrudRepository<Book, Long> {

        private final Map<Long, Book> books;

        public InMemoryRepository(Map<Long, Book> books) {
            this.books = books;
        }

        @Override
        public <S extends Book> Mono<S> save(S entity) {
            this.books.put(entity.getId(), entity);
            return Mono.just(entity);
        }

        @Override
        public <S extends Book> Flux<S> saveAll(Iterable<S> entities) {
            return Flux.fromIterable(entities).doOnNext(this::store);
        }

        @Override
        public <S extends Book> Flux<S> saveAll(Publisher<S> entityStream) {
            return Flux.from(entityStream).doOnNext(this::store);
        }

        private <S extends Book> void store(S entity) {
            this.books.put(entity.getId(), entity);
        }

        @Override
        public Mono<Book> findById(Long id) {
            return Mono.justOrEmpty(this.books.get(id));
        }

        @Override
        public Mono<Book> findById(Publisher<Long> id) {
            return Mono.from(id).flatMap(this::findById);
        }

        @Override
        public Mono<Boolean> existsById(Long id) {
            return Mono.just(this.books.containsKey(id));
        }

        @Override
        public Mono<Boolean> existsById(Publisher<Long> id) {
            return Mono.from(id).map(this.books::containsKey);
        }

        @Override
        public Flux<Book> findAll() {
            return Flux.fromIterable(new ArrayList<>(this.books.values()));
        }

        @Override
        public Flux<Book> findAllById(Iterable<Long> ids) {
            return Flux.fromIterable(ids).concatMap(this::findById);
        }

        @Override
        public Flux<Book> findAllById(Publisher<Long> ids) {
            return Flux.from(ids).concatMap(this::findById);
        }

        @Override
        public Mono<Long> count() {
            return Mono.just((long) this.books.size());
        }

        @Override
        public Mono<Void> deleteById(Long id) {
            return Mono.fromRunnable(() -> this.books.remove(id));
        }

        @Override
        public Mono<Void> deleteById(Publisher<Long> id) {
            return Mono.from(id).doOnNext(this.books::remove).then();
        }

        @Override
        public Mono<Void> delete(Book entity) {
            return Mono.fromRunnable(() -> this.books.remove(entity.getId()));
        }

        @Override
        public Mono<Void> deleteAllById(Iterable<? extends Long> ids) {
            return Flux.fromIterable(ids).doOnNext(this.books::remove).then();
        }

        @Override
        public Mono<Void> deleteAll(Iterable<? extends Book> entities) {
            return Flux.fromIterable(entities).doOnNext(entity -> this.books.remove(entity.getId())).then();
        }

        @Override
        public Mono<Void> deleteAll(Publisher<? extends Book> entityStream) {
            return Flux.from(entityStream).doOnNext(entity -> this.books.remove(entity.getId())).then();
        }

        @Override
        public Mono<Void> deleteAll() {
            return Mono.fromRunnable(this.books::clear);
        }
    }
}
