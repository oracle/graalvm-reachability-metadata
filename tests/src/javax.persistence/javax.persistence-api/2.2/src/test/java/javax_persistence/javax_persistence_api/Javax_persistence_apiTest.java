/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_persistence.javax_persistence_api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import javax.persistence.AccessType;
import javax.persistence.Cache;
import javax.persistence.CacheRetrieveMode;
import javax.persistence.CacheStoreMode;
import javax.persistence.CascadeType;
import javax.persistence.ConstraintMode;
import javax.persistence.DiscriminatorType;
import javax.persistence.EntityExistsException;
import javax.persistence.EntityGraph;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityNotFoundException;
import javax.persistence.EnumType;
import javax.persistence.FetchType;
import javax.persistence.FlushModeType;
import javax.persistence.GenerationType;
import javax.persistence.InheritanceType;
import javax.persistence.LockModeType;
import javax.persistence.LockTimeoutException;
import javax.persistence.NoResultException;
import javax.persistence.NonUniqueResultException;
import javax.persistence.OptimisticLockException;
import javax.persistence.Parameter;
import javax.persistence.ParameterMode;
import javax.persistence.Persistence;
import javax.persistence.PersistenceContextType;
import javax.persistence.PersistenceException;
import javax.persistence.PersistenceUnitUtil;
import javax.persistence.PessimisticLockException;
import javax.persistence.PessimisticLockScope;
import javax.persistence.Query;
import javax.persistence.QueryTimeoutException;
import javax.persistence.RollbackException;
import javax.persistence.SharedCacheMode;
import javax.persistence.StoredProcedureQuery;
import javax.persistence.SynchronizationType;
import javax.persistence.TemporalType;
import javax.persistence.TransactionRequiredException;
import javax.persistence.TypedQuery;
import javax.persistence.ValidationMode;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.Predicate;
import javax.persistence.metamodel.Attribute;
import javax.persistence.metamodel.Bindable;
import javax.persistence.metamodel.Metamodel;
import javax.persistence.metamodel.PluralAttribute;
import javax.persistence.metamodel.Type;
import javax.persistence.spi.LoadState;
import javax.persistence.spi.PersistenceProvider;
import javax.persistence.spi.PersistenceProviderResolver;
import javax.persistence.spi.PersistenceProviderResolverHolder;
import javax.persistence.spi.PersistenceUnitInfo;
import javax.persistence.spi.PersistenceUnitTransactionType;
import javax.persistence.spi.ProviderUtil;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class Javax_persistence_apiTest {

    private PersistenceProviderResolver originalResolver;
    private ClassLoader originalContextClassLoader;

    @BeforeEach
    void captureOriginalState() {
        originalResolver = PersistenceProviderResolverHolder.getPersistenceProviderResolver();
        originalContextClassLoader = Thread.currentThread().getContextClassLoader();
    }

    @AfterEach
    void restoreOriginalState() {
        Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        PersistenceProviderResolverHolder.setPersistenceProviderResolver(originalResolver);
    }

    @Test
    void createEntityManagerFactoryUsesProvidersInOrderAndPreservesInputs() {
        SimpleEntityManagerFactory entityManagerFactory = new SimpleEntityManagerFactory(Map.of("dialect", "demo"));
        RecordingProvider rejectingProvider = new RecordingProvider(null, false, unknownProviderUtil());
        RecordingProvider acceptingProvider = new RecordingProvider(entityManagerFactory, false, unknownProviderUtil());
        RecordingResolver resolver = new RecordingResolver(List.of(rejectingProvider, acceptingProvider));
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("schema", "public");
        properties.put("generate-ddl", true);

        PersistenceProviderResolverHolder.setPersistenceProviderResolver(resolver);

        EntityManagerFactory returnedFactory = Persistence.createEntityManagerFactory("orders", properties);

        assertThat(returnedFactory).isSameAs(entityManagerFactory);
        assertThat(resolver.getPersistenceProvidersCalls).hasValue(1);
        assertThat(rejectingProvider.entityManagerFactoryCalls).hasValue(1);
        assertThat(acceptingProvider.entityManagerFactoryCalls).hasValue(1);
        assertThat(rejectingProvider.lastEntityManagerFactoryUnitName).isEqualTo("orders");
        assertThat(acceptingProvider.lastEntityManagerFactoryUnitName).isEqualTo("orders");
        assertThat(rejectingProvider.lastEntityManagerFactoryProperties).isSameAs(properties);
        assertThat(acceptingProvider.lastEntityManagerFactoryProperties).isSameAs(properties);

        RecordingProvider defaultOverloadProvider = new RecordingProvider(entityManagerFactory, false, unknownProviderUtil());
        PersistenceProviderResolverHolder.setPersistenceProviderResolver(new RecordingResolver(List.of(defaultOverloadProvider)));

        EntityManagerFactory returnedWithoutProperties = Persistence.createEntityManagerFactory("orders-default");

        assertThat(returnedWithoutProperties).isSameAs(entityManagerFactory);
        assertThat(defaultOverloadProvider.lastEntityManagerFactoryUnitName).isEqualTo("orders-default");
        assertThat(defaultOverloadProvider.lastEntityManagerFactoryProperties).isNull();
    }

    @Test
    void createEntityManagerFactoryThrowsMeaningfulErrorWhenNoProviderMatches() {
        RecordingProvider provider = new RecordingProvider(null, false, unknownProviderUtil());
        PersistenceProviderResolverHolder.setPersistenceProviderResolver(new RecordingResolver(List.of(provider)));

        assertThatThrownBy(() -> Persistence.createEntityManagerFactory("missing-unit", Map.of("tenant", "blue")))
                .isInstanceOf(PersistenceException.class)
                .hasMessage("No Persistence provider for EntityManager named missing-unit");

        assertThat(provider.entityManagerFactoryCalls).hasValue(1);
    }

    @Test
    void generateSchemaStopsAtTheFirstProviderThatSucceedsAndThrowsOtherwise() {
        RecordingProvider firstProvider = new RecordingProvider(null, false, unknownProviderUtil());
        RecordingProvider secondProvider = new RecordingProvider(null, true, unknownProviderUtil());
        RecordingResolver successResolver = new RecordingResolver(List.of(firstProvider, secondProvider));
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("scripts.action", "create");

        PersistenceProviderResolverHolder.setPersistenceProviderResolver(successResolver);
        Persistence.generateSchema("catalog", properties);

        assertThat(firstProvider.generateSchemaCalls).hasValue(1);
        assertThat(secondProvider.generateSchemaCalls).hasValue(1);
        assertThat(firstProvider.lastGenerateSchemaUnitName).isEqualTo("catalog");
        assertThat(secondProvider.lastGenerateSchemaUnitName).isEqualTo("catalog");
        assertThat(secondProvider.lastGenerateSchemaProperties).isSameAs(properties);

        RecordingProvider failingProvider = new RecordingProvider(null, false, unknownProviderUtil());
        PersistenceProviderResolverHolder.setPersistenceProviderResolver(new RecordingResolver(List.of(failingProvider)));

        assertThatThrownBy(() -> Persistence.generateSchema("missing-schema", Map.of()))
                .isInstanceOf(PersistenceException.class)
                .hasMessage("No Persistence provider to generate schema named missing-schema");
    }

    @Test
    void persistenceUtilResolvesLoadStateAcrossBothLookupPhasesAndFallbacks() {
        Object entity = new Object();
        PersistenceProviderResolverHolder.setPersistenceProviderResolver(new RecordingResolver(
                List.of(new RecordingProvider(null, false, new RecordingProviderUtil(LoadState.LOADED, LoadState.UNKNOWN, LoadState.UNKNOWN)))));
        assertThat(Persistence.getPersistenceUtil().isLoaded(entity, "orders")).isTrue();

        PersistenceProviderResolverHolder.setPersistenceProviderResolver(new RecordingResolver(
                List.of(new RecordingProvider(null, false, new RecordingProviderUtil(LoadState.NOT_LOADED, LoadState.UNKNOWN, LoadState.UNKNOWN)))));
        assertThat(Persistence.getPersistenceUtil().isLoaded(entity, "orders")).isFalse();

        PersistenceProviderResolverHolder.setPersistenceProviderResolver(new RecordingResolver(
                List.of(new RecordingProvider(null, false, new RecordingProviderUtil(LoadState.UNKNOWN, LoadState.LOADED, LoadState.UNKNOWN)))));
        assertThat(Persistence.getPersistenceUtil().isLoaded(entity, "orders")).isTrue();

        PersistenceProviderResolverHolder.setPersistenceProviderResolver(new RecordingResolver(
                List.of(new RecordingProvider(null, false, new RecordingProviderUtil(LoadState.UNKNOWN, LoadState.NOT_LOADED, LoadState.UNKNOWN)))));
        assertThat(Persistence.getPersistenceUtil().isLoaded(entity, "orders")).isFalse();

        PersistenceProviderResolverHolder.setPersistenceProviderResolver(new RecordingResolver(
                List.of(new RecordingProvider(null, false, new RecordingProviderUtil(LoadState.UNKNOWN, LoadState.UNKNOWN, LoadState.UNKNOWN)))));
        assertThat(Persistence.getPersistenceUtil().isLoaded(entity, "orders")).isTrue();

        PersistenceProviderResolverHolder.setPersistenceProviderResolver(new RecordingResolver(
                List.of(new RecordingProvider(null, false, new RecordingProviderUtil(LoadState.UNKNOWN, LoadState.UNKNOWN, LoadState.LOADED)))));
        assertThat(Persistence.getPersistenceUtil().isLoaded(entity)).isTrue();

        PersistenceProviderResolverHolder.setPersistenceProviderResolver(new RecordingResolver(
                List.of(new RecordingProvider(null, false, new RecordingProviderUtil(LoadState.UNKNOWN, LoadState.UNKNOWN, LoadState.NOT_LOADED)))));
        assertThat(Persistence.getPersistenceUtil().isLoaded(entity)).isFalse();

        PersistenceProviderResolverHolder.setPersistenceProviderResolver(new RecordingResolver(
                List.of(new RecordingProvider(null, false, new RecordingProviderUtil(LoadState.UNKNOWN, LoadState.UNKNOWN, LoadState.UNKNOWN)))));
        assertThat(Persistence.getPersistenceUtil().isLoaded(entity)).isTrue();
    }

    @Test
    void queryAndTypedQueryDefaultMethodsDelegateToTheirConcreteImplementations() {
        RecordingQuery query = new RecordingQuery(List.of("alpha", "beta"), "single-result");
        Parameter<String> stringParameter = new SimpleParameter<>("name", 1, String.class);
        Parameter<Calendar> calendarParameter = new SimpleParameter<>("calendar", 2, Calendar.class);
        Parameter<Date> dateParameter = new SimpleParameter<>("date", 3, Date.class);
        Calendar calendar = Calendar.getInstance();
        Date date = new Date(123L);

        assertThat(query.getResultStream().toList()).containsExactly("alpha", "beta");

        RecordingTypedQuery<String> typedQuery = new RecordingTypedQuery<>(List.of("first", "second"), "single");
        assertThat(typedQuery.getResultStream().toList()).containsExactly("first", "second");

        Query typedAsQuery = typedQuery;
        assertThat(typedAsQuery.setMaxResults(7)).isSameAs(typedQuery);
        assertThat(typedAsQuery.setFirstResult(2)).isSameAs(typedQuery);
        assertThat(typedAsQuery.setHint("fetch-size", 50)).isSameAs(typedQuery);
        assertThat(typedAsQuery.setParameter(stringParameter, "Ada")).isSameAs(typedQuery);
        assertThat(typedAsQuery.setParameter(calendarParameter, calendar, TemporalType.TIMESTAMP)).isSameAs(typedQuery);
        assertThat(typedAsQuery.setParameter(dateParameter, date, TemporalType.DATE)).isSameAs(typedQuery);
        assertThat(typedAsQuery.setParameter("named", "value")).isSameAs(typedQuery);
        assertThat(typedAsQuery.setParameter("named-calendar", calendar, TemporalType.TIME)).isSameAs(typedQuery);
        assertThat(typedAsQuery.setParameter("named-date", date, TemporalType.TIMESTAMP)).isSameAs(typedQuery);
        assertThat(typedAsQuery.setParameter(3, "indexed")).isSameAs(typedQuery);
        assertThat(typedAsQuery.setParameter(4, calendar, TemporalType.DATE)).isSameAs(typedQuery);
        assertThat(typedAsQuery.setParameter(5, date, TemporalType.TIME)).isSameAs(typedQuery);
        assertThat(typedAsQuery.setFlushMode(FlushModeType.COMMIT)).isSameAs(typedQuery);
        assertThat(typedAsQuery.setLockMode(LockModeType.PESSIMISTIC_WRITE)).isSameAs(typedQuery);

        assertThat(typedQuery.getMaxResults()).isEqualTo(7);
        assertThat(typedQuery.getFirstResult()).isEqualTo(2);
        assertThat(typedQuery.getHints()).containsEntry("fetch-size", 50);
        assertThat(typedQuery.getFlushMode()).isEqualTo(FlushModeType.COMMIT);
        assertThat(typedQuery.getLockMode()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
        assertThat(typedQuery.namedParameters).containsEntry("named", "value");
        assertThat(typedQuery.indexedParameters).containsEntry(3, "indexed");
        assertThat(typedQuery.parameterValues).containsEntry(stringParameter, "Ada");
        assertThat(typedQuery.parameterValues).containsEntry(calendarParameter, calendar);
        assertThat(typedQuery.parameterValues).containsEntry(dateParameter, date);
    }

    @Test
    void storedProcedureQueryBridgeMethodsDelegateThroughTheQueryView() {
        RecordingStoredProcedureQuery storedProcedureQuery = new RecordingStoredProcedureQuery(List.of("row-1"), "single-row");
        Parameter<String> stringParameter = new SimpleParameter<>("proc", 8, String.class);
        Parameter<Calendar> calendarParameter = new SimpleParameter<>("proc-calendar", 9, Calendar.class);
        Parameter<Date> dateParameter = new SimpleParameter<>("proc-date", 10, Date.class);
        Calendar calendar = Calendar.getInstance();
        Date date = new Date(456L);
        Query queryView = storedProcedureQuery;

        assertThat(queryView.setHint("timeout", 15)).isSameAs(storedProcedureQuery);
        assertThat(queryView.setParameter(stringParameter, "value")).isSameAs(storedProcedureQuery);
        assertThat(queryView.setParameter(calendarParameter, calendar, TemporalType.DATE)).isSameAs(storedProcedureQuery);
        assertThat(queryView.setParameter(dateParameter, date, TemporalType.TIMESTAMP)).isSameAs(storedProcedureQuery);
        assertThat(queryView.setParameter("named", "argument")).isSameAs(storedProcedureQuery);
        assertThat(queryView.setParameter("named-calendar", calendar, TemporalType.TIME)).isSameAs(storedProcedureQuery);
        assertThat(queryView.setParameter("named-date", date, TemporalType.DATE)).isSameAs(storedProcedureQuery);
        assertThat(queryView.setParameter(1, "indexed")).isSameAs(storedProcedureQuery);
        assertThat(queryView.setParameter(2, calendar, TemporalType.TIMESTAMP)).isSameAs(storedProcedureQuery);
        assertThat(queryView.setParameter(3, date, TemporalType.TIME)).isSameAs(storedProcedureQuery);
        assertThat(queryView.setFlushMode(FlushModeType.AUTO)).isSameAs(storedProcedureQuery);

        assertThat(storedProcedureQuery.getHints()).containsEntry("timeout", 15);
        assertThat(storedProcedureQuery.parameterValues).containsEntry(stringParameter, "value");
        assertThat(storedProcedureQuery.parameterValues).containsEntry(calendarParameter, calendar);
        assertThat(storedProcedureQuery.parameterValues).containsEntry(dateParameter, date);
        assertThat(storedProcedureQuery.namedParameters).containsEntry("named", "argument");
        assertThat(storedProcedureQuery.indexedParameters).containsEntry(1, "indexed");
        assertThat(storedProcedureQuery.getFlushMode()).isEqualTo(FlushModeType.AUTO);
        assertThat(storedProcedureQuery.getResultStream().toList()).containsExactly("row-1");
    }

    @Test
    void defaultResolverReturnsAnEmptyProviderListWhenNoServicesAreVisible() throws Exception {
        Path servicesRoot = Files.createTempDirectory("jpa-empty-services");
        Thread.currentThread().setContextClassLoader(new URLClassLoader(new URL[]{servicesRoot.toUri().toURL()}, getClass().getClassLoader()));
        PersistenceProviderResolverHolder.setPersistenceProviderResolver(null);

        List<PersistenceProvider> providers = PersistenceProviderResolverHolder.getPersistenceProviderResolver().getPersistenceProviders();

        assertThat(providers).isEmpty();
        assertThat(Persistence.getPersistenceUtil().isLoaded(new Object())).isTrue();
    }

    @Test
    void defaultResolverLoadsProvidersFromServiceLoaderAndCachesThemForTheClassLoader() throws Exception {
        Path servicesRoot = Files.createTempDirectory("jpa-services");
        Path serviceFile = servicesRoot.resolve("META-INF/services/" + PersistenceProvider.class.getName());
        Files.createDirectories(serviceFile.getParent());
        Files.writeString(
                serviceFile,
                ServiceLoadedPersistenceProvider.class.getName() + System.lineSeparator() + "does.not.ExistProvider" + System.lineSeparator(),
                StandardCharsets.UTF_8);

        Thread.currentThread().setContextClassLoader(new URLClassLoader(new URL[]{servicesRoot.toUri().toURL()}, getClass().getClassLoader()));
        PersistenceProviderResolverHolder.setPersistenceProviderResolver(null);

        PersistenceProviderResolver resolver = PersistenceProviderResolverHolder.getPersistenceProviderResolver();
        List<PersistenceProvider> firstLookup = resolver.getPersistenceProviders();
        List<PersistenceProvider> secondLookup = resolver.getPersistenceProviders();

        assertThat(firstLookup).hasSize(1);
        assertThat(firstLookup.get(0)).isInstanceOf(ServiceLoadedPersistenceProvider.class);
        assertThat(secondLookup).isSameAs(firstLookup);
    }

    @Test
    void defaultResolverClearCachedProvidersReloadsServicesForTheCurrentClassLoader() throws Exception {
        Path servicesRoot = Files.createTempDirectory("jpa-services-refresh");
        Path serviceFile = servicesRoot.resolve("META-INF/services/" + PersistenceProvider.class.getName());
        Files.createDirectories(serviceFile.getParent());
        Files.writeString(serviceFile, ServiceLoadedPersistenceProvider.class.getName() + System.lineSeparator(), StandardCharsets.UTF_8);

        Thread.currentThread().setContextClassLoader(new URLClassLoader(new URL[]{servicesRoot.toUri().toURL()}, getClass().getClassLoader()));
        PersistenceProviderResolverHolder.setPersistenceProviderResolver(null);

        PersistenceProviderResolver resolver = PersistenceProviderResolverHolder.getPersistenceProviderResolver();
        List<PersistenceProvider> initialLookup = resolver.getPersistenceProviders();

        Files.writeString(serviceFile, AlternateServiceLoadedPersistenceProvider.class.getName() + System.lineSeparator(), StandardCharsets.UTF_8);

        List<PersistenceProvider> cachedLookup = resolver.getPersistenceProviders();
        resolver.clearCachedProviders();
        List<PersistenceProvider> refreshedLookup = resolver.getPersistenceProviders();

        assertThat(initialLookup).hasSize(1);
        assertThat(initialLookup.get(0)).isInstanceOf(ServiceLoadedPersistenceProvider.class);
        assertThat(cachedLookup).isSameAs(initialLookup);
        assertThat(refreshedLookup).hasSize(1);
        assertThat(refreshedLookup).isNotSameAs(initialLookup);
        assertThat(refreshedLookup.get(0)).isInstanceOf(AlternateServiceLoadedPersistenceProvider.class);
    }

    @Test
    void defaultResolverKeepsSeparateProviderCachesPerContextClassLoader() throws Exception {
        Path firstServicesRoot = Files.createTempDirectory("jpa-services-first-loader");
        Path firstServiceFile = firstServicesRoot.resolve("META-INF/services/" + PersistenceProvider.class.getName());
        Files.createDirectories(firstServiceFile.getParent());
        Files.writeString(firstServiceFile, ServiceLoadedPersistenceProvider.class.getName() + System.lineSeparator(), StandardCharsets.UTF_8);

        Path secondServicesRoot = Files.createTempDirectory("jpa-services-second-loader");
        Path secondServiceFile = secondServicesRoot.resolve("META-INF/services/" + PersistenceProvider.class.getName());
        Files.createDirectories(secondServiceFile.getParent());
        Files.writeString(secondServiceFile, AlternateServiceLoadedPersistenceProvider.class.getName() + System.lineSeparator(), StandardCharsets.UTF_8);

        try (URLClassLoader firstClassLoader = new URLClassLoader(new URL[]{firstServicesRoot.toUri().toURL()}, getClass().getClassLoader());
                URLClassLoader secondClassLoader = new URLClassLoader(new URL[]{secondServicesRoot.toUri().toURL()}, getClass().getClassLoader())) {
            PersistenceProviderResolverHolder.setPersistenceProviderResolver(null);
            PersistenceProviderResolver resolver = PersistenceProviderResolverHolder.getPersistenceProviderResolver();

            Thread.currentThread().setContextClassLoader(firstClassLoader);
            List<PersistenceProvider> firstLookup = resolver.getPersistenceProviders();

            Thread.currentThread().setContextClassLoader(secondClassLoader);
            List<PersistenceProvider> secondLookup = resolver.getPersistenceProviders();

            Thread.currentThread().setContextClassLoader(firstClassLoader);
            List<PersistenceProvider> repeatedFirstLookup = resolver.getPersistenceProviders();

            assertThat(firstLookup).hasSize(1);
            assertThat(firstLookup.get(0)).isInstanceOf(ServiceLoadedPersistenceProvider.class);
            assertThat(secondLookup).hasSize(1);
            assertThat(secondLookup.get(0)).isInstanceOf(AlternateServiceLoadedPersistenceProvider.class);
            assertThat(secondLookup).isNotSameAs(firstLookup);
            assertThat(repeatedFirstLookup).isSameAs(firstLookup);
        }
    }

    @Test
    void publicEnumsExceptionsAndUtilityTypesExposeExpectedContracts() {
        Query query = new RecordingQuery(List.of("row"), "single-row");
        Object entity = new Object();
        Throwable cause = new IllegalStateException("boom");

        assertThat(new Persistence()).isNotNull();
        assertThat(new PersistenceProviderResolverHolder()).isNotNull();
        assertThat(Persistence.PERSISTENCE_PROVIDER).isEqualTo("javax.persistence.spi.PeristenceProvider");

        assertEnumContract(AccessType.values(), "FIELD", AccessType.FIELD);
        assertEnumContract(Attribute.PersistentAttributeType.values(), "MANY_TO_ONE", Attribute.PersistentAttributeType.MANY_TO_ONE);
        assertEnumContract(Bindable.BindableType.values(), "ENTITY_TYPE", Bindable.BindableType.ENTITY_TYPE);
        assertEnumContract(CacheRetrieveMode.values(), "USE", CacheRetrieveMode.USE);
        assertEnumContract(CacheStoreMode.values(), "REFRESH", CacheStoreMode.REFRESH);
        assertEnumContract(CascadeType.values(), "ALL", CascadeType.ALL);
        assertEnumContract(ConstraintMode.values(), "NO_CONSTRAINT", ConstraintMode.NO_CONSTRAINT);
        assertEnumContract(CriteriaBuilder.Trimspec.values(), "BOTH", CriteriaBuilder.Trimspec.BOTH);
        assertEnumContract(DiscriminatorType.values(), "STRING", DiscriminatorType.STRING);
        assertEnumContract(EnumType.values(), "STRING", EnumType.STRING);
        assertEnumContract(FetchType.values(), "LAZY", FetchType.LAZY);
        assertEnumContract(FlushModeType.values(), "AUTO", FlushModeType.AUTO);
        assertEnumContract(GenerationType.values(), "TABLE", GenerationType.TABLE);
        assertEnumContract(InheritanceType.values(), "TABLE_PER_CLASS", InheritanceType.TABLE_PER_CLASS);
        assertEnumContract(JoinType.values(), "LEFT", JoinType.LEFT);
        assertEnumContract(LoadState.values(), "UNKNOWN", LoadState.UNKNOWN);
        assertEnumContract(LockModeType.values(), "PESSIMISTIC_FORCE_INCREMENT", LockModeType.PESSIMISTIC_FORCE_INCREMENT);
        assertEnumContract(ParameterMode.values(), "REF_CURSOR", ParameterMode.REF_CURSOR);
        assertEnumContract(PersistenceContextType.values(), "EXTENDED", PersistenceContextType.EXTENDED);
        assertEnumContract(PersistenceUnitTransactionType.values(), "RESOURCE_LOCAL", PersistenceUnitTransactionType.RESOURCE_LOCAL);
        assertEnumContract(PessimisticLockScope.values(), "EXTENDED", PessimisticLockScope.EXTENDED);
        assertEnumContract(PluralAttribute.CollectionType.values(), "MAP", PluralAttribute.CollectionType.MAP);
        assertEnumContract(Predicate.BooleanOperator.values(), "OR", Predicate.BooleanOperator.OR);
        assertEnumContract(SharedCacheMode.values(), "UNSPECIFIED", SharedCacheMode.UNSPECIFIED);
        assertEnumContract(SynchronizationType.values(), "UNSYNCHRONIZED", SynchronizationType.UNSYNCHRONIZED);
        assertEnumContract(TemporalType.values(), "TIMESTAMP", TemporalType.TIMESTAMP);
        assertEnumContract(Type.PersistenceType.values(), "ENTITY", Type.PersistenceType.ENTITY);
        assertEnumContract(ValidationMode.values(), "CALLBACK", ValidationMode.CALLBACK);

        assertThat(new PersistenceException()).hasMessage(null).hasNoCause();
        assertThat(new PersistenceException("message")).hasMessage("message").hasNoCause();
        assertThat(new PersistenceException("message", cause)).hasMessage("message").hasCause(cause);
        assertThat(new PersistenceException(cause)).hasCause(cause);

        assertThat(new EntityExistsException()).hasMessage(null).hasNoCause();
        assertThat(new EntityExistsException("exists")).hasMessage("exists");
        assertThat(new EntityExistsException("exists", cause)).hasMessage("exists").hasCause(cause);
        assertThat(new EntityExistsException(cause)).hasCause(cause);

        assertThat(new EntityNotFoundException()).hasMessage(null).hasNoCause();
        assertThat(new EntityNotFoundException("missing")).hasMessage("missing");

        LockTimeoutException lockTimeoutWithEntity = new LockTimeoutException(entity);
        assertThat(lockTimeoutWithEntity.getObject()).isSameAs(entity);
        assertThat(new LockTimeoutException()).hasMessage(null).hasNoCause();
        assertThat(new LockTimeoutException("timeout")).hasMessage("timeout");
        assertThat(new LockTimeoutException("timeout", cause)).hasMessage("timeout").hasCause(cause);
        assertThat(new LockTimeoutException(cause)).hasCause(cause);
        assertThat(new LockTimeoutException("timeout", cause, entity).getObject()).isSameAs(entity);

        assertThat(new NoResultException()).hasMessage(null).hasNoCause();
        assertThat(new NoResultException("none")).hasMessage("none");

        assertThat(new NonUniqueResultException()).hasMessage(null).hasNoCause();
        assertThat(new NonUniqueResultException("many")).hasMessage("many");

        OptimisticLockException optimisticLockWithEntity = new OptimisticLockException(entity);
        assertThat(optimisticLockWithEntity.getEntity()).isSameAs(entity);
        assertThat(new OptimisticLockException()).hasMessage(null).hasNoCause();
        assertThat(new OptimisticLockException("optimistic")).hasMessage("optimistic");
        assertThat(new OptimisticLockException("optimistic", cause)).hasMessage("optimistic").hasCause(cause);
        assertThat(new OptimisticLockException(cause)).hasCause(cause);
        assertThat(new OptimisticLockException("optimistic", cause, entity).getEntity()).isSameAs(entity);

        PessimisticLockException pessimisticLockWithEntity = new PessimisticLockException(entity);
        assertThat(pessimisticLockWithEntity.getEntity()).isSameAs(entity);
        assertThat(new PessimisticLockException()).hasMessage(null).hasNoCause();
        assertThat(new PessimisticLockException("pessimistic")).hasMessage("pessimistic");
        assertThat(new PessimisticLockException("pessimistic", cause)).hasMessage("pessimistic").hasCause(cause);
        assertThat(new PessimisticLockException(cause)).hasCause(cause);
        assertThat(new PessimisticLockException("pessimistic", cause, entity).getEntity()).isSameAs(entity);

        QueryTimeoutException queryTimeoutWithQuery = new QueryTimeoutException(query);
        assertThat(queryTimeoutWithQuery.getQuery()).isSameAs(query);
        assertThat(new QueryTimeoutException()).hasMessage(null).hasNoCause();
        assertThat(new QueryTimeoutException("query-timeout")).hasMessage("query-timeout");
        assertThat(new QueryTimeoutException("query-timeout", cause)).hasMessage("query-timeout").hasCause(cause);
        assertThat(new QueryTimeoutException(cause)).hasCause(cause);
        assertThat(new QueryTimeoutException("query-timeout", cause, query).getQuery()).isSameAs(query);

        assertThat(new RollbackException()).hasMessage(null).hasNoCause();
        assertThat(new RollbackException("rolled back")).hasMessage("rolled back");
        assertThat(new RollbackException("rolled back", cause)).hasMessage("rolled back").hasCause(cause);
        assertThat(new RollbackException(cause)).hasCause(cause);

        assertThat(new TransactionRequiredException()).hasMessage(null).hasNoCause();
        assertThat(new TransactionRequiredException("required")).hasMessage("required");
    }

    private static ProviderUtil unknownProviderUtil() {
        return new RecordingProviderUtil(LoadState.UNKNOWN, LoadState.UNKNOWN, LoadState.UNKNOWN);
    }

    private static <E extends Enum<E>> void assertEnumContract(E[] values, String enumName, E expectedValue) {
        assertThat(values).contains(expectedValue);
        assertThat(Enum.valueOf(expectedValue.getDeclaringClass(), enumName)).isSameAs(expectedValue);
    }

    private static final class RecordingResolver implements PersistenceProviderResolver {
        private final List<PersistenceProvider> providers;
        private final AtomicInteger getPersistenceProvidersCalls = new AtomicInteger();

        private RecordingResolver(List<PersistenceProvider> providers) {
            this.providers = providers;
        }

        @Override
        public List<PersistenceProvider> getPersistenceProviders() {
            getPersistenceProvidersCalls.incrementAndGet();
            return providers;
        }

        @Override
        public void clearCachedProviders() {
        }
    }

    private static class RecordingProvider implements PersistenceProvider {
        private final EntityManagerFactory entityManagerFactory;
        private final boolean generateSchemaResult;
        private final ProviderUtil providerUtil;
        private final AtomicInteger entityManagerFactoryCalls = new AtomicInteger();
        private final AtomicInteger generateSchemaCalls = new AtomicInteger();
        private String lastEntityManagerFactoryUnitName;
        private Map<?, ?> lastEntityManagerFactoryProperties;
        private String lastGenerateSchemaUnitName;
        private Map<?, ?> lastGenerateSchemaProperties;

        private RecordingProvider(EntityManagerFactory entityManagerFactory, boolean generateSchemaResult, ProviderUtil providerUtil) {
            this.entityManagerFactory = entityManagerFactory;
            this.generateSchemaResult = generateSchemaResult;
            this.providerUtil = providerUtil;
        }

        @Override
        public EntityManagerFactory createEntityManagerFactory(String emName, Map map) {
            entityManagerFactoryCalls.incrementAndGet();
            lastEntityManagerFactoryUnitName = emName;
            lastEntityManagerFactoryProperties = map;
            return entityManagerFactory;
        }

        @Override
        public EntityManagerFactory createContainerEntityManagerFactory(PersistenceUnitInfo info, Map map) {
            return entityManagerFactory;
        }

        @Override
        public void generateSchema(PersistenceUnitInfo info, Map map) {
        }

        @Override
        public boolean generateSchema(String persistenceUnitName, Map map) {
            generateSchemaCalls.incrementAndGet();
            lastGenerateSchemaUnitName = persistenceUnitName;
            lastGenerateSchemaProperties = map;
            return generateSchemaResult;
        }

        @Override
        public ProviderUtil getProviderUtil() {
            return providerUtil;
        }
    }

    public static final class ServiceLoadedPersistenceProvider extends RecordingProvider {
        public ServiceLoadedPersistenceProvider() {
            super(new SimpleEntityManagerFactory(Map.of("provider", "service-loader")), true, unknownProviderUtil());
        }
    }

    public static final class AlternateServiceLoadedPersistenceProvider extends RecordingProvider {
        public AlternateServiceLoadedPersistenceProvider() {
            super(new SimpleEntityManagerFactory(Map.of("provider", "service-loader-alternate")), true, unknownProviderUtil());
        }
    }

    private static final class RecordingProviderUtil implements ProviderUtil {
        private final LoadState withoutReferenceLoadState;
        private final LoadState withReferenceLoadState;
        private final LoadState loadState;

        private RecordingProviderUtil(LoadState withoutReferenceLoadState, LoadState withReferenceLoadState, LoadState loadState) {
            this.withoutReferenceLoadState = withoutReferenceLoadState;
            this.withReferenceLoadState = withReferenceLoadState;
            this.loadState = loadState;
        }

        @Override
        public LoadState isLoadedWithoutReference(Object entity, String attributeName) {
            return withoutReferenceLoadState;
        }

        @Override
        public LoadState isLoadedWithReference(Object entity, String attributeName) {
            return withReferenceLoadState;
        }

        @Override
        public LoadState isLoaded(Object entity) {
            return loadState;
        }
    }

    private static final class SimpleEntityManagerFactory implements EntityManagerFactory {
        private final Map<String, Object> properties;
        private boolean open = true;
        private final Map<String, Query> namedQueries = new LinkedHashMap<>();
        private final Map<String, EntityGraph<?>> namedEntityGraphs = new LinkedHashMap<>();

        private SimpleEntityManagerFactory(Map<String, Object> properties) {
            this.properties = new LinkedHashMap<>(properties);
        }

        @Override
        public EntityManager createEntityManager() {
            return null;
        }

        @Override
        public EntityManager createEntityManager(Map map) {
            return null;
        }

        @Override
        public EntityManager createEntityManager(SynchronizationType synchronizationType) {
            return null;
        }

        @Override
        public EntityManager createEntityManager(SynchronizationType synchronizationType, Map map) {
            return null;
        }

        @Override
        public CriteriaBuilder getCriteriaBuilder() {
            return null;
        }

        @Override
        public Metamodel getMetamodel() {
            return null;
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public void close() {
            open = false;
        }

        @Override
        public Map<String, Object> getProperties() {
            return Collections.unmodifiableMap(properties);
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
            namedQueries.put(name, query);
        }

        @Override
        public <T> T unwrap(Class<T> cls) {
            if (cls.isInstance(this)) {
                return cls.cast(this);
            }
            throw new IllegalArgumentException("Cannot unwrap to " + cls.getName());
        }

        @Override
        public <T> void addNamedEntityGraph(String graphName, EntityGraph<T> entityGraph) {
            namedEntityGraphs.put(graphName, entityGraph);
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
        private final List<?> resultList;
        private final Object singleResult;
        private int maxResults;
        private int firstResult;
        private final Map<String, Object> hints = new LinkedHashMap<>();
        private final Map<Parameter<?>, Object> parameterValues = new LinkedHashMap<>();
        private final Map<String, Object> namedParameters = new LinkedHashMap<>();
        private final Map<Integer, Object> indexedParameters = new LinkedHashMap<>();
        private FlushModeType flushMode = FlushModeType.AUTO;
        private LockModeType lockMode = LockModeType.NONE;

        private RecordingQuery(List<?> resultList, Object singleResult) {
            this.resultList = new ArrayList<>(resultList);
            this.singleResult = singleResult;
        }

        @Override
        public List getResultList() {
            return resultList;
        }

        @Override
        public Object getSingleResult() {
            return singleResult;
        }

        @Override
        public int executeUpdate() {
            return 1;
        }

        @Override
        public Query setMaxResults(int maxResult) {
            maxResults = maxResult;
            return this;
        }

        @Override
        public int getMaxResults() {
            return maxResults;
        }

        @Override
        public Query setFirstResult(int startPosition) {
            firstResult = startPosition;
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
            return hints;
        }

        @Override
        public <T> Query setParameter(Parameter<T> param, T value) {
            parameterValues.put(param, value);
            return this;
        }

        @Override
        public Query setParameter(Parameter<Calendar> param, Calendar value, TemporalType temporalType) {
            parameterValues.put(param, value);
            return this;
        }

        @Override
        public Query setParameter(Parameter<Date> param, Date value, TemporalType temporalType) {
            parameterValues.put(param, value);
            return this;
        }

        @Override
        public Query setParameter(String name, Object value) {
            namedParameters.put(name, value);
            return this;
        }

        @Override
        public Query setParameter(String name, Calendar value, TemporalType temporalType) {
            namedParameters.put(name, value);
            return this;
        }

        @Override
        public Query setParameter(String name, Date value, TemporalType temporalType) {
            namedParameters.put(name, value);
            return this;
        }

        @Override
        public Query setParameter(int position, Object value) {
            indexedParameters.put(position, value);
            return this;
        }

        @Override
        public Query setParameter(int position, Calendar value, TemporalType temporalType) {
            indexedParameters.put(position, value);
            return this;
        }

        @Override
        public Query setParameter(int position, Date value, TemporalType temporalType) {
            indexedParameters.put(position, value);
            return this;
        }

        @Override
        public Set<Parameter<?>> getParameters() {
            return new LinkedHashSet<>(parameterValues.keySet());
        }

        @Override
        public Parameter<?> getParameter(String name) {
            return parameterValues.keySet().stream().filter(parameter -> name.equals(parameter.getName())).findFirst().orElse(null);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> Parameter<T> getParameter(String name, Class<T> type) {
            return (Parameter<T>) getParameter(name);
        }

        @Override
        public Parameter<?> getParameter(int position) {
            return parameterValues.keySet().stream().filter(parameter -> Integer.valueOf(position).equals(parameter.getPosition())).findFirst().orElse(null);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> Parameter<T> getParameter(int position, Class<T> type) {
            return (Parameter<T>) getParameter(position);
        }

        @Override
        public boolean isBound(Parameter<?> param) {
            return parameterValues.containsKey(param);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getParameterValue(Parameter<T> param) {
            return (T) parameterValues.get(param);
        }

        @Override
        public Object getParameterValue(String name) {
            return namedParameters.get(name);
        }

        @Override
        public Object getParameterValue(int position) {
            return indexedParameters.get(position);
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
            throw new IllegalArgumentException("Cannot unwrap to " + cls.getName());
        }
    }

    private static final class RecordingTypedQuery<T> implements TypedQuery<T> {
        private final List<T> resultList;
        private final T singleResult;
        private int maxResults;
        private int firstResult;
        private final Map<String, Object> hints = new LinkedHashMap<>();
        private final Map<Parameter<?>, Object> parameterValues = new LinkedHashMap<>();
        private final Map<String, Object> namedParameters = new LinkedHashMap<>();
        private final Map<Integer, Object> indexedParameters = new LinkedHashMap<>();
        private FlushModeType flushMode = FlushModeType.AUTO;
        private LockModeType lockMode = LockModeType.NONE;

        private RecordingTypedQuery(List<T> resultList, T singleResult) {
            this.resultList = new ArrayList<>(resultList);
            this.singleResult = singleResult;
        }

        @Override
        public List<T> getResultList() {
            return resultList;
        }

        @Override
        public T getSingleResult() {
            return singleResult;
        }

        @Override
        public int executeUpdate() {
            return 1;
        }

        @Override
        public TypedQuery<T> setMaxResults(int maxResult) {
            maxResults = maxResult;
            return this;
        }

        @Override
        public int getMaxResults() {
            return maxResults;
        }

        @Override
        public TypedQuery<T> setFirstResult(int startPosition) {
            firstResult = startPosition;
            return this;
        }

        @Override
        public int getFirstResult() {
            return firstResult;
        }

        @Override
        public TypedQuery<T> setHint(String hintName, Object value) {
            hints.put(hintName, value);
            return this;
        }

        @Override
        public Map<String, Object> getHints() {
            return hints;
        }

        @Override
        public <P> TypedQuery<T> setParameter(Parameter<P> param, P value) {
            parameterValues.put(param, value);
            return this;
        }

        @Override
        public TypedQuery<T> setParameter(Parameter<Calendar> param, Calendar value, TemporalType temporalType) {
            parameterValues.put(param, value);
            return this;
        }

        @Override
        public TypedQuery<T> setParameter(Parameter<Date> param, Date value, TemporalType temporalType) {
            parameterValues.put(param, value);
            return this;
        }

        @Override
        public TypedQuery<T> setParameter(String name, Object value) {
            namedParameters.put(name, value);
            return this;
        }

        @Override
        public TypedQuery<T> setParameter(String name, Calendar value, TemporalType temporalType) {
            namedParameters.put(name, value);
            return this;
        }

        @Override
        public TypedQuery<T> setParameter(String name, Date value, TemporalType temporalType) {
            namedParameters.put(name, value);
            return this;
        }

        @Override
        public TypedQuery<T> setParameter(int position, Object value) {
            indexedParameters.put(position, value);
            return this;
        }

        @Override
        public TypedQuery<T> setParameter(int position, Calendar value, TemporalType temporalType) {
            indexedParameters.put(position, value);
            return this;
        }

        @Override
        public TypedQuery<T> setParameter(int position, Date value, TemporalType temporalType) {
            indexedParameters.put(position, value);
            return this;
        }

        @Override
        public Set<Parameter<?>> getParameters() {
            return new LinkedHashSet<>(parameterValues.keySet());
        }

        @Override
        public Parameter<?> getParameter(String name) {
            return parameterValues.keySet().stream().filter(parameter -> name.equals(parameter.getName())).findFirst().orElse(null);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <P> Parameter<P> getParameter(String name, Class<P> type) {
            return (Parameter<P>) getParameter(name);
        }

        @Override
        public Parameter<?> getParameter(int position) {
            return parameterValues.keySet().stream().filter(parameter -> Integer.valueOf(position).equals(parameter.getPosition())).findFirst().orElse(null);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <P> Parameter<P> getParameter(int position, Class<P> type) {
            return (Parameter<P>) getParameter(position);
        }

        @Override
        public boolean isBound(Parameter<?> param) {
            return parameterValues.containsKey(param);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <P> P getParameterValue(Parameter<P> param) {
            return (P) parameterValues.get(param);
        }

        @Override
        public Object getParameterValue(String name) {
            return namedParameters.get(name);
        }

        @Override
        public Object getParameterValue(int position) {
            return indexedParameters.get(position);
        }

        @Override
        public TypedQuery<T> setFlushMode(FlushModeType flushMode) {
            this.flushMode = flushMode;
            return this;
        }

        @Override
        public FlushModeType getFlushMode() {
            return flushMode;
        }

        @Override
        public TypedQuery<T> setLockMode(LockModeType lockMode) {
            this.lockMode = lockMode;
            return this;
        }

        @Override
        public LockModeType getLockMode() {
            return lockMode;
        }

        @Override
        public <U> U unwrap(Class<U> cls) {
            if (cls.isInstance(this)) {
                return cls.cast(this);
            }
            throw new IllegalArgumentException("Cannot unwrap to " + cls.getName());
        }
    }

    private static final class RecordingStoredProcedureQuery implements StoredProcedureQuery {
        private final List<?> resultList;
        private final Object singleResult;
        private final Map<String, Object> hints = new LinkedHashMap<>();
        private final Map<Parameter<?>, Object> parameterValues = new LinkedHashMap<>();
        private final Map<String, Object> namedParameters = new LinkedHashMap<>();
        private final Map<Integer, Object> indexedParameters = new LinkedHashMap<>();
        private final Map<Object, ParameterMode> registeredParameters = new LinkedHashMap<>();
        private int maxResults;
        private int firstResult;
        private FlushModeType flushMode = FlushModeType.AUTO;
        private LockModeType lockMode = LockModeType.NONE;

        private RecordingStoredProcedureQuery(List<?> resultList, Object singleResult) {
            this.resultList = new ArrayList<>(resultList);
            this.singleResult = singleResult;
        }

        @Override
        public StoredProcedureQuery setHint(String hintName, Object value) {
            hints.put(hintName, value);
            return this;
        }

        @Override
        public <T> StoredProcedureQuery setParameter(Parameter<T> param, T value) {
            parameterValues.put(param, value);
            return this;
        }

        @Override
        public StoredProcedureQuery setParameter(Parameter<Calendar> param, Calendar value, TemporalType temporalType) {
            parameterValues.put(param, value);
            return this;
        }

        @Override
        public StoredProcedureQuery setParameter(Parameter<Date> param, Date value, TemporalType temporalType) {
            parameterValues.put(param, value);
            return this;
        }

        @Override
        public StoredProcedureQuery setParameter(String name, Object value) {
            namedParameters.put(name, value);
            return this;
        }

        @Override
        public StoredProcedureQuery setParameter(String name, Calendar value, TemporalType temporalType) {
            namedParameters.put(name, value);
            return this;
        }

        @Override
        public StoredProcedureQuery setParameter(String name, Date value, TemporalType temporalType) {
            namedParameters.put(name, value);
            return this;
        }

        @Override
        public StoredProcedureQuery setParameter(int position, Object value) {
            indexedParameters.put(position, value);
            return this;
        }

        @Override
        public StoredProcedureQuery setParameter(int position, Calendar value, TemporalType temporalType) {
            indexedParameters.put(position, value);
            return this;
        }

        @Override
        public StoredProcedureQuery setParameter(int position, Date value, TemporalType temporalType) {
            indexedParameters.put(position, value);
            return this;
        }

        @Override
        public StoredProcedureQuery setFlushMode(FlushModeType flushMode) {
            this.flushMode = flushMode;
            return this;
        }

        @Override
        public StoredProcedureQuery registerStoredProcedureParameter(int position, Class type, ParameterMode mode) {
            registeredParameters.put(position, mode);
            return this;
        }

        @Override
        public StoredProcedureQuery registerStoredProcedureParameter(String parameterName, Class type, ParameterMode mode) {
            registeredParameters.put(parameterName, mode);
            return this;
        }

        @Override
        public Object getOutputParameterValue(int position) {
            return indexedParameters.get(position);
        }

        @Override
        public Object getOutputParameterValue(String parameterName) {
            return namedParameters.get(parameterName);
        }

        @Override
        public boolean execute() {
            return true;
        }

        @Override
        public int executeUpdate() {
            return 1;
        }

        @Override
        public List getResultList() {
            return resultList;
        }

        @Override
        public Object getSingleResult() {
            return singleResult;
        }

        @Override
        public boolean hasMoreResults() {
            return false;
        }

        @Override
        public int getUpdateCount() {
            return 1;
        }

        @Override
        public Query setMaxResults(int maxResult) {
            maxResults = maxResult;
            return this;
        }

        @Override
        public int getMaxResults() {
            return maxResults;
        }

        @Override
        public Query setFirstResult(int startPosition) {
            firstResult = startPosition;
            return this;
        }

        @Override
        public int getFirstResult() {
            return firstResult;
        }

        @Override
        public Map<String, Object> getHints() {
            return hints;
        }

        @Override
        public Set<Parameter<?>> getParameters() {
            return new LinkedHashSet<>(parameterValues.keySet());
        }

        @Override
        public Parameter<?> getParameter(String name) {
            return parameterValues.keySet().stream().filter(parameter -> name.equals(parameter.getName())).findFirst().orElse(null);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> Parameter<T> getParameter(String name, Class<T> type) {
            return (Parameter<T>) getParameter(name);
        }

        @Override
        public Parameter<?> getParameter(int position) {
            return parameterValues.keySet().stream().filter(parameter -> Integer.valueOf(position).equals(parameter.getPosition())).findFirst().orElse(null);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> Parameter<T> getParameter(int position, Class<T> type) {
            return (Parameter<T>) getParameter(position);
        }

        @Override
        public boolean isBound(Parameter<?> param) {
            return parameterValues.containsKey(param);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getParameterValue(Parameter<T> param) {
            return (T) parameterValues.get(param);
        }

        @Override
        public Object getParameterValue(String name) {
            return namedParameters.get(name);
        }

        @Override
        public Object getParameterValue(int position) {
            return indexedParameters.get(position);
        }

        @Override
        public LockModeType getLockMode() {
            return lockMode;
        }

        @Override
        public Query setLockMode(LockModeType lockMode) {
            this.lockMode = lockMode;
            return this;
        }

        @Override
        public FlushModeType getFlushMode() {
            return flushMode;
        }

        @Override
        public <T> T unwrap(Class<T> cls) {
            if (cls.isInstance(this)) {
                return cls.cast(this);
            }
            throw new IllegalArgumentException("Cannot unwrap to " + cls.getName());
        }
    }
}
