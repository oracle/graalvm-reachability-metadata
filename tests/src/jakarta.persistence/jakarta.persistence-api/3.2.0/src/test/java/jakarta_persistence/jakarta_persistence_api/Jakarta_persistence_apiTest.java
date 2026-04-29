/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_persistence.jakarta_persistence_api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import jakarta.persistence.Cache;
import jakarta.persistence.EntityExistsException;
import jakarta.persistence.EntityGraph;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.FlushModeType;
import jakarta.persistence.LockModeType;
import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.NoResultException;
import jakarta.persistence.NonUniqueResultException;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.Parameter;
import jakarta.persistence.Persistence;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.PersistenceUnitUtil;
import jakarta.persistence.PessimisticLockException;
import jakarta.persistence.Query;
import jakarta.persistence.QueryTimeoutException;
import jakarta.persistence.RollbackException;
import jakarta.persistence.SynchronizationType;
import jakarta.persistence.TemporalType;
import jakarta.persistence.TransactionRequiredException;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.metamodel.Metamodel;
import jakarta.persistence.spi.LoadState;
import jakarta.persistence.spi.PersistenceProvider;
import jakarta.persistence.spi.PersistenceProviderResolver;
import jakarta.persistence.spi.PersistenceProviderResolverHolder;
import jakarta.persistence.spi.PersistenceUnitInfo;
import jakarta.persistence.spi.ProviderUtil;
import org.junit.jupiter.api.Test;

public class Jakarta_persistence_apiTest {

    @Test
    void createEntityManagerFactoryUsesConfiguredProvidersInOrder() {
        RecordingProvider nullProvider = new RecordingProvider(
                "null-provider",
                null,
                false,
                new RecordingProviderUtil(LoadState.UNKNOWN, LoadState.UNKNOWN, LoadState.UNKNOWN)
        );
        StubEntityManagerFactory entityManagerFactory = new StubEntityManagerFactory();
        RecordingProvider successProvider = new RecordingProvider(
                "success-provider",
                entityManagerFactory,
                false,
                new RecordingProviderUtil(LoadState.UNKNOWN, LoadState.UNKNOWN, LoadState.UNKNOWN)
        );
        FixedResolver resolver = new FixedResolver(List.of(nullProvider, successProvider));
        Map<String, Object> properties = Map.of("jakarta.persistence.jdbc.url", "jdbc:test");

        try (ResolverScope ignored = useResolver(resolver)) {
            EntityManagerFactory factoryWithoutProperties = Persistence.createEntityManagerFactory("default-unit");
            EntityManagerFactory factoryWithProperties = Persistence.createEntityManagerFactory("custom-unit", properties);

            assertThat(factoryWithoutProperties).isSameAs(entityManagerFactory);
            assertThat(factoryWithProperties).isSameAs(entityManagerFactory);
            assertThat(resolver.getPersistenceProvidersCalls).isEqualTo(2);
            assertThat(nullProvider.createEntityManagerFactoryCalls)
                    .containsExactly(
                            new CreateEntityManagerFactoryCall("default-unit", null),
                            new CreateEntityManagerFactoryCall("custom-unit", properties)
                    );
            assertThat(successProvider.createEntityManagerFactoryCalls)
                    .containsExactly(
                            new CreateEntityManagerFactoryCall("default-unit", null),
                            new CreateEntityManagerFactoryCall("custom-unit", properties)
                    );
        }
    }

    @Test
    void createEntityManagerFactoryThrowsHelpfulMessageWhenNoProviderMatches() {
        FixedResolver emptyResolver = new FixedResolver(List.of());

        try (ResolverScope ignored = useResolver(emptyResolver)) {
            assertThatThrownBy(() -> Persistence.createEntityManagerFactory("missing-unit"))
                    .isInstanceOf(PersistenceException.class)
                    .hasMessage("No Persistence provider for EntityManager named missing-unit");
        }
    }

    @Test
    void createEntityManagerFactoryPropagatesProviderFailuresWithoutTryingLaterProviders() {
        IllegalStateException failure = new IllegalStateException("bootstrap-failed");
        ThrowingProvider failingProvider = new ThrowingProvider(
                failure,
                null,
                new RecordingProviderUtil(LoadState.UNKNOWN, LoadState.UNKNOWN, LoadState.UNKNOWN)
        );
        RecordingProvider fallbackProvider = new RecordingProvider(
                "fallback-provider",
                new StubEntityManagerFactory(),
                false,
                new RecordingProviderUtil(LoadState.UNKNOWN, LoadState.UNKNOWN, LoadState.UNKNOWN)
        );
        FixedResolver resolver = new FixedResolver(List.of(failingProvider, fallbackProvider));
        Map<String, Object> properties = Map.of("jakarta.persistence.provider", "test-provider");

        try (ResolverScope ignored = useResolver(resolver)) {
            assertThatThrownBy(() -> Persistence.createEntityManagerFactory("failing-unit", properties))
                    .isSameAs(failure);
            assertThat(failingProvider.createEntityManagerFactoryCalls)
                    .containsExactly(new CreateEntityManagerFactoryCall("failing-unit", properties));
            assertThat(fallbackProvider.createEntityManagerFactoryCalls).isEmpty();
        }
    }

    @Test
    void generateSchemaStopsAfterFirstProviderThatSucceeds() {
        RecordingProvider firstProvider = new RecordingProvider(
                "first-provider",
                null,
                false,
                new RecordingProviderUtil(LoadState.UNKNOWN, LoadState.UNKNOWN, LoadState.UNKNOWN)
        );
        RecordingProvider secondProvider = new RecordingProvider(
                "second-provider",
                null,
                true,
                new RecordingProviderUtil(LoadState.UNKNOWN, LoadState.UNKNOWN, LoadState.UNKNOWN)
        );
        RecordingProvider thirdProvider = new RecordingProvider(
                "third-provider",
                null,
                true,
                new RecordingProviderUtil(LoadState.UNKNOWN, LoadState.UNKNOWN, LoadState.UNKNOWN)
        );
        FixedResolver resolver = new FixedResolver(List.of(firstProvider, secondProvider, thirdProvider));
        Map<String, Object> properties = Map.of("jakarta.persistence.schema-generation.database.action", "drop-and-create");

        try (ResolverScope ignored = useResolver(resolver)) {
            Persistence.generateSchema("schema-unit", properties);

            assertThat(firstProvider.generateSchemaCalls)
                    .containsExactly(new GenerateSchemaCall("schema-unit", properties));
            assertThat(secondProvider.generateSchemaCalls)
                    .containsExactly(new GenerateSchemaCall("schema-unit", properties));
            assertThat(thirdProvider.generateSchemaCalls).isEmpty();
        }
    }

    @Test
    void generateSchemaThrowsHelpfulMessageWhenNoProviderSucceeds() {
        FixedResolver resolver = new FixedResolver(List.of(
                new RecordingProvider(
                        "only-provider",
                        null,
                        false,
                        new RecordingProviderUtil(LoadState.UNKNOWN, LoadState.UNKNOWN, LoadState.UNKNOWN)
                )
        ));

        try (ResolverScope ignored = useResolver(resolver)) {
            assertThatThrownBy(() -> Persistence.generateSchema("missing-schema-unit", Map.of()))
                    .isInstanceOf(PersistenceException.class)
                    .hasMessage("No Persistence provider to generate schema named missing-schema-unit");
        }
    }

    @Test
    void generateSchemaPropagatesProviderFailuresWithoutTryingLaterProviders() {
        IllegalArgumentException failure = new IllegalArgumentException("schema-failed");
        ThrowingProvider failingProvider = new ThrowingProvider(
                null,
                failure,
                new RecordingProviderUtil(LoadState.UNKNOWN, LoadState.UNKNOWN, LoadState.UNKNOWN)
        );
        RecordingProvider fallbackProvider = new RecordingProvider(
                "fallback-provider",
                null,
                true,
                new RecordingProviderUtil(LoadState.UNKNOWN, LoadState.UNKNOWN, LoadState.UNKNOWN)
        );
        FixedResolver resolver = new FixedResolver(List.of(failingProvider, fallbackProvider));
        Map<String, Object> properties = Map.of("jakarta.persistence.schema-generation.scripts.action", "create");

        try (ResolverScope ignored = useResolver(resolver)) {
            assertThatThrownBy(() -> Persistence.generateSchema("failing-schema-unit", properties))
                    .isSameAs(failure);
            assertThat(failingProvider.generateSchemaCalls)
                    .containsExactly(new GenerateSchemaCall("failing-schema-unit", properties));
            assertThat(fallbackProvider.generateSchemaCalls).isEmpty();
        }
    }

    @Test
    void persistenceUtilUsesWithoutReferenceBeforeWithReference() {
        Object entity = new Object();
        RecordingProviderUtil firstProviderUtil = new RecordingProviderUtil(
                LoadState.UNKNOWN,
                LoadState.UNKNOWN,
                LoadState.UNKNOWN
        );
        RecordingProviderUtil secondProviderUtil = new RecordingProviderUtil(
                LoadState.UNKNOWN,
                LoadState.NOT_LOADED,
                LoadState.LOADED
        );
        FixedResolver resolver = new FixedResolver(List.of(
                new RecordingProvider("first", null, false, firstProviderUtil),
                new RecordingProvider("second", null, false, secondProviderUtil)
        ));

        try (ResolverScope ignored = useResolver(resolver)) {
            boolean loaded = Persistence.getPersistenceUtil().isLoaded(entity, "orders");

            assertThat(loaded).isFalse();
            assertThat(firstProviderUtil.withoutReferenceCalls).isEqualTo(1);
            assertThat(secondProviderUtil.withoutReferenceCalls).isEqualTo(1);
            assertThat(firstProviderUtil.withReferenceCalls).isZero();
            assertThat(secondProviderUtil.withReferenceCalls).isZero();
            assertThat(secondProviderUtil.lastAttributeName).isEqualTo("orders");
        }
    }

    @Test
    void persistenceUtilFallsBackToWithReferenceAndDefaultsToLoadedForUnknownState() {
        Object entity = new Object();
        RecordingProviderUtil firstProviderUtil = new RecordingProviderUtil(
                LoadState.UNKNOWN,
                LoadState.UNKNOWN,
                LoadState.UNKNOWN
        );
        RecordingProviderUtil secondProviderUtil = new RecordingProviderUtil(
                LoadState.UNKNOWN,
                LoadState.UNKNOWN,
                LoadState.LOADED
        );
        RecordingProviderUtil objectStateUtil = new RecordingProviderUtil(
                LoadState.UNKNOWN,
                LoadState.UNKNOWN,
                LoadState.UNKNOWN
        );
        FixedResolver resolver = new FixedResolver(List.of(
                new RecordingProvider("first", null, false, firstProviderUtil),
                new RecordingProvider("second", null, false, secondProviderUtil),
                new RecordingProvider("third", null, false, objectStateUtil)
        ));

        try (ResolverScope ignored = useResolver(resolver)) {
            boolean attributeLoaded = Persistence.getPersistenceUtil().isLoaded(entity, "customer");
            boolean entityLoaded = Persistence.getPersistenceUtil().isLoaded(entity);

            assertThat(attributeLoaded).isTrue();
            assertThat(entityLoaded).isTrue();
            assertThat(firstProviderUtil.withReferenceCalls).isEqualTo(1);
            assertThat(secondProviderUtil.withReferenceCalls).isEqualTo(1);
            assertThat(objectStateUtil.isLoadedCalls).isEqualTo(1);
        }
    }

    @Test
    void queryAndTypedQueryDefaultMethodsDelegateToImplementedOperations() {
        RecordingQuery query = new RecordingQuery(List.of("alpha", "beta"));
        RecordingTypedQuery<String> typedQuery = new RecordingTypedQuery<>(List.of("one", "two"), "one");
        Query rawTypedQuery = typedQuery;
        SimpleParameter<String> titleParameter = new SimpleParameter<>("title", null, String.class);
        Calendar calendar = Calendar.getInstance();
        Date date = new Date(1234L);

        assertThat(query.getResultStream().toList()).containsExactly("alpha", "beta");
        assertThat(typedQuery.getResultStream().toList()).containsExactly("one", "two");

        assertThat(rawTypedQuery.setMaxResults(25)).isSameAs(typedQuery);
        assertThat(rawTypedQuery.setFirstResult(5)).isSameAs(typedQuery);
        assertThat(rawTypedQuery.setHint("graph", "books")).isSameAs(typedQuery);
        assertThat(rawTypedQuery.setParameter(titleParameter, "Jakarta Persistence")).isSameAs(typedQuery);
        assertThat(rawTypedQuery.setParameter("createdBy", "test")).isSameAs(typedQuery);
        assertThat(rawTypedQuery.setParameter("createdAt", calendar, TemporalType.TIMESTAMP)).isSameAs(typedQuery);
        assertThat(rawTypedQuery.setParameter(1, 42)).isSameAs(typedQuery);
        assertThat(rawTypedQuery.setParameter(2, date, TemporalType.DATE)).isSameAs(typedQuery);
        assertThat(rawTypedQuery.setFlushMode(FlushModeType.COMMIT)).isSameAs(typedQuery);
        assertThat(rawTypedQuery.setLockMode(LockModeType.PESSIMISTIC_READ)).isSameAs(typedQuery);

        assertThat(typedQuery.getMaxResults()).isEqualTo(25);
        assertThat(typedQuery.getFirstResult()).isEqualTo(5);
        assertThat(typedQuery.getHints()).containsEntry("graph", "books");
        assertThat(typedQuery.getParameterValue("createdBy")).isEqualTo("test");
        assertThat(typedQuery.getParameterValue(1)).isEqualTo(42);
        assertThat(((RecordingQuery) typedQuery).getNamedTemporalType("createdAt"))
                .isEqualTo(TemporalType.TIMESTAMP);
        assertThat(((RecordingQuery) typedQuery).getIndexedTemporalType(2))
                .isEqualTo(TemporalType.DATE);
        assertThat(typedQuery.getParameterValue(titleParameter)).isEqualTo("Jakarta Persistence");
        assertThat(typedQuery.getFlushMode()).isEqualTo(FlushModeType.COMMIT);
        assertThat(typedQuery.getLockMode()).isEqualTo(LockModeType.PESSIMISTIC_READ);
    }

    @Test
    void persistenceExceptionsRetainMessagesCausesAndAssociatedObjects() {
        RuntimeException cause = new RuntimeException("boom");
        Object entity = new Object();
        RecordingQuery query = new RecordingQuery(List.of("value"));

        PersistenceException persistenceException = new PersistenceException("persistence", cause);
        EntityExistsException entityExistsException = new EntityExistsException(cause);
        EntityNotFoundException entityNotFoundException = new EntityNotFoundException("missing-entity");
        LockTimeoutException lockTimeoutException = new LockTimeoutException("lock-timeout", cause, entity);
        NoResultException noResultException = new NoResultException("no-result");
        NonUniqueResultException nonUniqueResultException = new NonUniqueResultException("too-many-results");
        OptimisticLockException optimisticLockException = new OptimisticLockException("optimistic", cause, entity);
        PessimisticLockException pessimisticLockException = new PessimisticLockException("pessimistic", cause, entity);
        QueryTimeoutException queryTimeoutException = new QueryTimeoutException("query-timeout", cause, query);
        RollbackException rollbackException = new RollbackException(cause);
        TransactionRequiredException transactionRequiredException = new TransactionRequiredException("tx-required");

        assertThat(persistenceException).hasMessage("persistence").hasCause(cause);
        assertThat(entityExistsException).hasCause(cause);
        assertThat(entityNotFoundException).hasMessage("missing-entity");
        assertThat(lockTimeoutException).hasMessage("lock-timeout").hasCause(cause);
        assertThat(lockTimeoutException.getObject()).isSameAs(entity);
        assertThat(noResultException).hasMessage("no-result");
        assertThat(nonUniqueResultException).hasMessage("too-many-results");
        assertThat(optimisticLockException).hasMessage("optimistic").hasCause(cause);
        assertThat(optimisticLockException.getEntity()).isSameAs(entity);
        assertThat(pessimisticLockException).hasMessage("pessimistic").hasCause(cause);
        assertThat(pessimisticLockException.getEntity()).isSameAs(entity);
        assertThat(queryTimeoutException).hasMessage("query-timeout").hasCause(cause);
        assertThat(queryTimeoutException.getQuery()).isSameAs(query);
        assertThat(rollbackException).hasCause(cause);
        assertThat(transactionRequiredException).hasMessage("tx-required");
    }

    @Test
    void resettingResolverToNullRestoresDefaultResolver() {
        PersistenceProviderResolver customResolver = new FixedResolver(List.of());
        PersistenceProviderResolver originalResolver = PersistenceProviderResolverHolder.getPersistenceProviderResolver();

        PersistenceProviderResolverHolder.setPersistenceProviderResolver(customResolver);
        try {
            assertThat(PersistenceProviderResolverHolder.getPersistenceProviderResolver()).isSameAs(customResolver);

            PersistenceProviderResolverHolder.setPersistenceProviderResolver(null);

            assertThat(PersistenceProviderResolverHolder.getPersistenceProviderResolver())
                    .isNotSameAs(customResolver)
                    .isNotNull();
        } finally {
            PersistenceProviderResolverHolder.setPersistenceProviderResolver(originalResolver);
        }
    }

    @Test
    void defaultResolverDiscoversProvidersFromContextClassLoaderAndReloadsAfterCacheClear() throws Exception {
        PersistenceProviderResolver originalResolver = PersistenceProviderResolverHolder.getPersistenceProviderResolver();
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        Path providerConfigDirectory = createProviderConfigDirectory(DiscoverableProvider.class.getName());
        DiscoverableProvider.reset();

        PersistenceProviderResolverHolder.setPersistenceProviderResolver(null);
        try (URLClassLoader providerClassLoader = new URLClassLoader(
                new java.net.URL[] {providerConfigDirectory.toUri().toURL()},
                Jakarta_persistence_apiTest.class.getClassLoader()
        )) {
            Thread.currentThread().setContextClassLoader(providerClassLoader);

            PersistenceProviderResolver resolver = PersistenceProviderResolverHolder.getPersistenceProviderResolver();
            List<PersistenceProvider> firstProviders = resolver.getPersistenceProviders();
            List<PersistenceProvider> secondProviders = resolver.getPersistenceProviders();

            assertThat(firstProviders)
                    .anySatisfy(provider -> assertThat(provider).isInstanceOf(DiscoverableProvider.class));
            assertThat(secondProviders).isSameAs(firstProviders);
            assertThat(DiscoverableProvider.getInstanceCount()).isEqualTo(1);

            resolver.clearCachedProviders();

            List<PersistenceProvider> reloadedProviders = resolver.getPersistenceProviders();

            assertThat(reloadedProviders)
                    .isNotSameAs(firstProviders)
                    .anySatisfy(provider -> assertThat(provider).isInstanceOf(DiscoverableProvider.class));
            assertThat(DiscoverableProvider.getInstanceCount()).isEqualTo(2);
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
            PersistenceProviderResolverHolder.setPersistenceProviderResolver(originalResolver);
            deleteRecursively(providerConfigDirectory);
        }
    }

    private static Path createProviderConfigDirectory(String... providerClassNames) throws IOException {
        Path configDirectory = Files.createTempDirectory("jakarta-persistence-provider");
        Path servicesDirectory = configDirectory.resolve("META-INF").resolve("services");
        Files.createDirectories(servicesDirectory);
        Files.writeString(
                servicesDirectory.resolve(PersistenceProvider.class.getName()),
                String.join(System.lineSeparator(), providerClassNames),
                StandardCharsets.UTF_8
        );
        return configDirectory;
    }

    private static void deleteRecursively(Path path) throws IOException {
        try (Stream<Path> paths = Files.walk(path)) {
            paths.sorted(Comparator.reverseOrder())
                    .forEach(currentPath -> {
                        try {
                            Files.deleteIfExists(currentPath);
                        } catch (IOException exception) {
                            throw new RuntimeException(exception);
                        }
                    });
        } catch (RuntimeException exception) {
            if (exception.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            throw exception;
        }
    }

    private static ResolverScope useResolver(PersistenceProviderResolver resolver) {
        PersistenceProviderResolver previousResolver =
                PersistenceProviderResolverHolder.getPersistenceProviderResolver();
        PersistenceProviderResolverHolder.setPersistenceProviderResolver(resolver);
        return new ResolverScope(previousResolver);
    }

    private record CreateEntityManagerFactoryCall(String unitName, Map<String, Object> properties) {
    }

    private record GenerateSchemaCall(String unitName, Map<String, Object> properties) {
    }

    private static final class ResolverScope implements AutoCloseable {
        private final PersistenceProviderResolver previousResolver;

        private ResolverScope(PersistenceProviderResolver previousResolver) {
            this.previousResolver = previousResolver;
        }

        @Override
        public void close() {
            PersistenceProviderResolverHolder.setPersistenceProviderResolver(previousResolver);
        }
    }

    private static final class FixedResolver implements PersistenceProviderResolver {
        private final List<PersistenceProvider> providers;
        private int getPersistenceProvidersCalls;

        private FixedResolver(List<PersistenceProvider> providers) {
            this.providers = List.copyOf(providers);
        }

        @Override
        public List<PersistenceProvider> getPersistenceProviders() {
            getPersistenceProvidersCalls++;
            return providers;
        }

        @Override
        public void clearCachedProviders() {
        }
    }

    private static final class RecordingProvider implements PersistenceProvider {
        private final String providerName;
        private final EntityManagerFactory entityManagerFactory;
        private final boolean generateSchemaResult;
        private final ProviderUtil providerUtil;
        private final List<CreateEntityManagerFactoryCall> createEntityManagerFactoryCalls = new ArrayList<>();
        private final List<GenerateSchemaCall> generateSchemaCalls = new ArrayList<>();

        private RecordingProvider(
                String providerName,
                EntityManagerFactory entityManagerFactory,
                boolean generateSchemaResult,
                ProviderUtil providerUtil
        ) {
            this.providerName = providerName;
            this.entityManagerFactory = entityManagerFactory;
            this.generateSchemaResult = generateSchemaResult;
            this.providerUtil = providerUtil;
        }

        @Override
        @SuppressWarnings("unchecked")
        public EntityManagerFactory createEntityManagerFactory(String emName, Map map) {
            createEntityManagerFactoryCalls.add(new CreateEntityManagerFactoryCall(emName, (Map<String, Object>) map));
            return entityManagerFactory;
        }

        @Override
        public EntityManagerFactory createContainerEntityManagerFactory(PersistenceUnitInfo info, Map map) {
            throw new UnsupportedOperationException(providerName);
        }

        @Override
        public void generateSchema(PersistenceUnitInfo info, Map map) {
            throw new UnsupportedOperationException(providerName);
        }

        @Override
        @SuppressWarnings("unchecked")
        public boolean generateSchema(String persistenceUnitName, Map map) {
            generateSchemaCalls.add(new GenerateSchemaCall(persistenceUnitName, (Map<String, Object>) map));
            return generateSchemaResult;
        }

        @Override
        public ProviderUtil getProviderUtil() {
            return providerUtil;
        }
    }

    private static final class ThrowingProvider implements PersistenceProvider {
        private final RuntimeException createEntityManagerFactoryFailure;
        private final RuntimeException generateSchemaFailure;
        private final ProviderUtil providerUtil;
        private final List<CreateEntityManagerFactoryCall> createEntityManagerFactoryCalls = new ArrayList<>();
        private final List<GenerateSchemaCall> generateSchemaCalls = new ArrayList<>();

        private ThrowingProvider(
                RuntimeException createEntityManagerFactoryFailure,
                RuntimeException generateSchemaFailure,
                ProviderUtil providerUtil
        ) {
            this.createEntityManagerFactoryFailure = createEntityManagerFactoryFailure;
            this.generateSchemaFailure = generateSchemaFailure;
            this.providerUtil = providerUtil;
        }

        @Override
        @SuppressWarnings("unchecked")
        public EntityManagerFactory createEntityManagerFactory(String emName, Map map) {
            createEntityManagerFactoryCalls.add(new CreateEntityManagerFactoryCall(emName, (Map<String, Object>) map));
            throw createEntityManagerFactoryFailure;
        }

        @Override
        public EntityManagerFactory createContainerEntityManagerFactory(PersistenceUnitInfo info, Map map) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void generateSchema(PersistenceUnitInfo info, Map map) {
            throw new UnsupportedOperationException();
        }

        @Override
        @SuppressWarnings("unchecked")
        public boolean generateSchema(String persistenceUnitName, Map map) {
            generateSchemaCalls.add(new GenerateSchemaCall(persistenceUnitName, (Map<String, Object>) map));
            throw generateSchemaFailure;
        }

        @Override
        public ProviderUtil getProviderUtil() {
            return providerUtil;
        }
    }

    public static final class DiscoverableProvider implements PersistenceProvider {
        private static final AtomicInteger INSTANCE_COUNT = new AtomicInteger();

        public DiscoverableProvider() {
            INSTANCE_COUNT.incrementAndGet();
        }

        static void reset() {
            INSTANCE_COUNT.set(0);
        }

        static int getInstanceCount() {
            return INSTANCE_COUNT.get();
        }

        @Override
        public EntityManagerFactory createEntityManagerFactory(String emName, Map map) {
            return null;
        }

        @Override
        public EntityManagerFactory createContainerEntityManagerFactory(PersistenceUnitInfo info, Map map) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void generateSchema(PersistenceUnitInfo info, Map map) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean generateSchema(String persistenceUnitName, Map map) {
            return false;
        }

        @Override
        public ProviderUtil getProviderUtil() {
            return new RecordingProviderUtil(LoadState.UNKNOWN, LoadState.UNKNOWN, LoadState.UNKNOWN);
        }
    }

    private static final class RecordingProviderUtil implements ProviderUtil {
        private final LoadState objectLoadState;
        private final LoadState withoutReferenceLoadState;
        private final LoadState withReferenceLoadState;
        private int isLoadedCalls;
        private int withoutReferenceCalls;
        private int withReferenceCalls;
        private String lastAttributeName;

        private RecordingProviderUtil(
                LoadState objectLoadState,
                LoadState withoutReferenceLoadState,
                LoadState withReferenceLoadState
        ) {
            this.objectLoadState = objectLoadState;
            this.withoutReferenceLoadState = withoutReferenceLoadState;
            this.withReferenceLoadState = withReferenceLoadState;
        }

        @Override
        public LoadState isLoadedWithoutReference(Object entity, String attributeName) {
            Objects.requireNonNull(entity);
            withoutReferenceCalls++;
            lastAttributeName = attributeName;
            return withoutReferenceLoadState;
        }

        @Override
        public LoadState isLoadedWithReference(Object entity, String attributeName) {
            Objects.requireNonNull(entity);
            withReferenceCalls++;
            lastAttributeName = attributeName;
            return withReferenceLoadState;
        }

        @Override
        public LoadState isLoaded(Object entity) {
            Objects.requireNonNull(entity);
            isLoadedCalls++;
            return objectLoadState;
        }
    }

    private static final class SimpleParameter<T> implements Parameter<T> {
        private final String name;
        private final Integer position;
        private final Class<T> parameterType;

        private SimpleParameter(String name, Integer position, Class<T> parameterType) {
            this.name = name;
            this.position = position;
            this.parameterType = parameterType;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Integer getPosition() {
            return position;
        }

        @Override
        public Class<T> getParameterType() {
            return parameterType;
        }
    }

    private static class RecordingQuery implements Query {
        private final List<Object> results;
        private final Object singleResult;
        private final Map<String, Object> hints = new LinkedHashMap<>();
        private final Map<Parameter<?>, Object> parameterValuesByParameter = new LinkedHashMap<>();
        private final Map<String, Object> parameterValuesByName = new LinkedHashMap<>();
        private final Map<Integer, Object> parameterValuesByPosition = new LinkedHashMap<>();
        private final Map<String, TemporalType> temporalValuesByName = new LinkedHashMap<>();
        private final Map<Integer, TemporalType> temporalValuesByPosition = new LinkedHashMap<>();
        private int maxResults;
        private int firstResult;
        private FlushModeType flushMode = FlushModeType.AUTO;
        private LockModeType lockMode = LockModeType.NONE;

        private RecordingQuery(List<?> results) {
            this(results, results.isEmpty() ? null : results.get(0));
        }

        private RecordingQuery(List<?> results, Object singleResult) {
            this.results = new ArrayList<>(results);
            this.singleResult = singleResult;
        }

        @Override
        @SuppressWarnings("rawtypes")
        public List getResultList() {
            return results;
        }

        @Override
        public Object getSingleResult() {
            return singleResult;
        }

        @Override
        public int executeUpdate() {
            return results.size();
        }

        @Override
        public Query setMaxResults(int maxResult) {
            this.maxResults = maxResult;
            return this;
        }

        @Override
        public int getMaxResults() {
            return maxResults;
        }

        @Override
        public Query setFirstResult(int startPosition) {
            this.firstResult = startPosition;
            return this;
        }

        @Override
        public int getFirstResult() {
            return firstResult;
        }

        @Override
        public Query setHint(String hintName, Object value) {
            hints.put(hintName, value);
            return this;
        }

        @Override
        public Map<String, Object> getHints() {
            return Collections.unmodifiableMap(hints);
        }

        @Override
        public <T> Query setParameter(Parameter<T> param, T value) {
            parameterValuesByParameter.put(param, value);
            return this;
        }

        @Override
        public Query setParameter(Parameter<Calendar> param, Calendar value, TemporalType temporalType) {
            parameterValuesByParameter.put(param, value);
            return this;
        }

        @Override
        public Query setParameter(Parameter<Date> param, Date value, TemporalType temporalType) {
            parameterValuesByParameter.put(param, value);
            return this;
        }

        @Override
        public Query setParameter(String name, Object value) {
            parameterValuesByName.put(name, value);
            return this;
        }

        @Override
        public Query setParameter(String name, Calendar value, TemporalType temporalType) {
            parameterValuesByName.put(name, value);
            temporalValuesByName.put(name, temporalType);
            return this;
        }

        @Override
        public Query setParameter(String name, Date value, TemporalType temporalType) {
            parameterValuesByName.put(name, value);
            temporalValuesByName.put(name, temporalType);
            return this;
        }

        @Override
        public Query setParameter(int position, Object value) {
            parameterValuesByPosition.put(position, value);
            return this;
        }

        @Override
        public Query setParameter(int position, Calendar value, TemporalType temporalType) {
            parameterValuesByPosition.put(position, value);
            temporalValuesByPosition.put(position, temporalType);
            return this;
        }

        @Override
        public Query setParameter(int position, Date value, TemporalType temporalType) {
            parameterValuesByPosition.put(position, value);
            temporalValuesByPosition.put(position, temporalType);
            return this;
        }

        @Override
        public Set<Parameter<?>> getParameters() {
            return parameterValuesByParameter.keySet();
        }

        @Override
        public Parameter<?> getParameter(String name) {
            throw new UnsupportedOperationException(name);
        }

        @Override
        public <T> Parameter<T> getParameter(String name, Class<T> type) {
            throw new UnsupportedOperationException(name + type.getName());
        }

        @Override
        public Parameter<?> getParameter(int position) {
            throw new UnsupportedOperationException(String.valueOf(position));
        }

        @Override
        public <T> Parameter<T> getParameter(int position, Class<T> type) {
            throw new UnsupportedOperationException(position + type.getName());
        }

        @Override
        public boolean isBound(Parameter<?> param) {
            return parameterValuesByParameter.containsKey(param);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getParameterValue(Parameter<T> param) {
            return (T) parameterValuesByParameter.get(param);
        }

        @Override
        public Object getParameterValue(String name) {
            return parameterValuesByName.get(name);
        }

        @Override
        public Object getParameterValue(int position) {
            return parameterValuesByPosition.get(position);
        }

        @Override
        public Query setFlushMode(FlushModeType flushMode) {
            this.flushMode = flushMode;
            return this;
        }

        @Override
        public FlushModeType getFlushMode() {
            return flushMode;
        }

        @Override
        public Query setLockMode(LockModeType lockMode) {
            this.lockMode = lockMode;
            return this;
        }

        @Override
        public LockModeType getLockMode() {
            return lockMode;
        }

        @Override
        public <T> T unwrap(Class<T> cls) {
            if (cls.isInstance(this)) {
                return cls.cast(this);
            }
            throw new IllegalArgumentException(cls.getName());
        }

        TemporalType getNamedTemporalType(String name) {
            return temporalValuesByName.get(name);
        }

        TemporalType getIndexedTemporalType(int position) {
            return temporalValuesByPosition.get(position);
        }
    }

    private static final class RecordingTypedQuery<T> extends RecordingQuery implements TypedQuery<T> {
        private final List<T> typedResults;
        private final T typedSingleResult;

        private RecordingTypedQuery(List<T> results, T singleResult) {
            super(results, singleResult);
            this.typedResults = new ArrayList<>(results);
            this.typedSingleResult = singleResult;
        }

        @Override
        public List<T> getResultList() {
            return typedResults;
        }

        @Override
        public T getSingleResult() {
            return typedSingleResult;
        }

        @Override
        public TypedQuery<T> setMaxResults(int maxResult) {
            super.setMaxResults(maxResult);
            return this;
        }

        @Override
        public TypedQuery<T> setFirstResult(int startPosition) {
            super.setFirstResult(startPosition);
            return this;
        }

        @Override
        public TypedQuery<T> setHint(String hintName, Object value) {
            super.setHint(hintName, value);
            return this;
        }

        @Override
        public <P> TypedQuery<T> setParameter(Parameter<P> param, P value) {
            super.setParameter(param, value);
            return this;
        }

        @Override
        public TypedQuery<T> setParameter(Parameter<Calendar> param, Calendar value, TemporalType temporalType) {
            super.setParameter(param, value, temporalType);
            return this;
        }

        @Override
        public TypedQuery<T> setParameter(Parameter<Date> param, Date value, TemporalType temporalType) {
            super.setParameter(param, value, temporalType);
            return this;
        }

        @Override
        public TypedQuery<T> setParameter(String name, Object value) {
            super.setParameter(name, value);
            return this;
        }

        @Override
        public TypedQuery<T> setParameter(String name, Calendar value, TemporalType temporalType) {
            super.setParameter(name, value, temporalType);
            return this;
        }

        @Override
        public TypedQuery<T> setParameter(String name, Date value, TemporalType temporalType) {
            super.setParameter(name, value, temporalType);
            return this;
        }

        @Override
        public TypedQuery<T> setParameter(int position, Object value) {
            super.setParameter(position, value);
            return this;
        }

        @Override
        public TypedQuery<T> setParameter(int position, Calendar value, TemporalType temporalType) {
            super.setParameter(position, value, temporalType);
            return this;
        }

        @Override
        public TypedQuery<T> setParameter(int position, Date value, TemporalType temporalType) {
            super.setParameter(position, value, temporalType);
            return this;
        }

        @Override
        public TypedQuery<T> setFlushMode(FlushModeType flushMode) {
            super.setFlushMode(flushMode);
            return this;
        }

        @Override
        public TypedQuery<T> setLockMode(LockModeType lockMode) {
            super.setLockMode(lockMode);
            return this;
        }
    }

    private static final class StubEntityManagerFactory implements EntityManagerFactory {
        @Override
        public EntityManager createEntityManager() {
            throw new UnsupportedOperationException();
        }

        @Override
        public EntityManager createEntityManager(Map map) {
            throw new UnsupportedOperationException();
        }

        @Override
        public EntityManager createEntityManager(SynchronizationType synchronizationType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public EntityManager createEntityManager(SynchronizationType synchronizationType, Map map) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CriteriaBuilder getCriteriaBuilder() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Metamodel getMetamodel() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isOpen() {
            return true;
        }

        @Override
        public void close() {
        }

        @Override
        public Map<String, Object> getProperties() {
            return Map.of();
        }

        @Override
        public Cache getCache() {
            return null;
        }

        @Override
        public PersistenceUnitUtil getPersistenceUnitUtil() {
            return null;
        }

        @Override
        public void addNamedQuery(String name, Query query) {
            throw new UnsupportedOperationException(name);
        }

        @Override
        public <T> T unwrap(Class<T> cls) {
            throw new IllegalArgumentException(cls.getName());
        }

        @Override
        public <T> void addNamedEntityGraph(String graphName, EntityGraph<T> entityGraph) {
            throw new UnsupportedOperationException(graphName);
        }
    }
}
