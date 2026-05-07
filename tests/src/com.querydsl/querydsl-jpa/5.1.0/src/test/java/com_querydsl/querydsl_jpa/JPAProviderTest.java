/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_querydsl.querydsl_jpa;

import java.util.List;
import java.util.Map;

import javax.persistence.Cache;
import javax.persistence.EntityGraph;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.FlushModeType;
import javax.persistence.LockModeType;
import javax.persistence.PersistenceException;
import javax.persistence.PersistenceUnitUtil;
import javax.persistence.Query;
import javax.persistence.StoredProcedureQuery;
import javax.persistence.SynchronizationType;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaDelete;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.CriteriaUpdate;
import javax.persistence.metamodel.Metamodel;

import com.querydsl.jpa.Hibernate5Templates;
import com.querydsl.jpa.JPQLTemplates;
import com.querydsl.jpa.impl.JPAProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JPAProviderTest {
    @Test
    void getTemplatesDetectsHibernateProviderFromEntityManagerFactoryProperties() {
        EntityManager entityManager = new StubEntityManager(new StubEntityManagerFactory(Map.of(
                "hibernate.connection.provider_class",
                "org.hibernate.engine.jdbc.connections.internal.DriverManagerConnectionProviderImpl")));

        JPQLTemplates templates = JPAProvider.getTemplates(entityManager);

        assertThat(templates).isSameAs(Hibernate5Templates.DEFAULT);
    }

    private static final class StubEntityManager implements EntityManager {
        private final EntityManagerFactory entityManagerFactory;
        private final Object delegate = new Object();

        private StubEntityManager(EntityManagerFactory entityManagerFactory) {
            this.entityManagerFactory = entityManagerFactory;
        }

        @Override
        public void persist(Object entity) {
            throw unsupported();
        }

        @Override
        public <T> T merge(T entity) {
            throw unsupported();
        }

        @Override
        public void remove(Object entity) {
            throw unsupported();
        }

        @Override
        public <T> T find(Class<T> entityClass, Object primaryKey) {
            throw unsupported();
        }

        @Override
        public <T> T find(Class<T> entityClass, Object primaryKey, Map<String, Object> properties) {
            throw unsupported();
        }

        @Override
        public <T> T find(Class<T> entityClass, Object primaryKey, LockModeType lockMode) {
            throw unsupported();
        }

        @Override
        public <T> T find(
                Class<T> entityClass,
                Object primaryKey,
                LockModeType lockMode,
                Map<String, Object> properties) {
            throw unsupported();
        }

        @Override
        public <T> T getReference(Class<T> entityClass, Object primaryKey) {
            throw unsupported();
        }

        @Override
        public void flush() {
            throw unsupported();
        }

        @Override
        public void setFlushMode(FlushModeType flushMode) {
            throw unsupported();
        }

        @Override
        public FlushModeType getFlushMode() {
            throw unsupported();
        }

        @Override
        public void lock(Object entity, LockModeType lockMode) {
            throw unsupported();
        }

        @Override
        public void lock(Object entity, LockModeType lockMode, Map<String, Object> properties) {
            throw unsupported();
        }

        @Override
        public void refresh(Object entity) {
            throw unsupported();
        }

        @Override
        public void refresh(Object entity, Map<String, Object> properties) {
            throw unsupported();
        }

        @Override
        public void refresh(Object entity, LockModeType lockMode) {
            throw unsupported();
        }

        @Override
        public void refresh(Object entity, LockModeType lockMode, Map<String, Object> properties) {
            throw unsupported();
        }

        @Override
        public void clear() {
            throw unsupported();
        }

        @Override
        public void detach(Object entity) {
            throw unsupported();
        }

        @Override
        public boolean contains(Object entity) {
            throw unsupported();
        }

        @Override
        public LockModeType getLockMode(Object entity) {
            throw unsupported();
        }

        @Override
        public void setProperty(String propertyName, Object value) {
            throw unsupported();
        }

        @Override
        public Map<String, Object> getProperties() {
            return entityManagerFactory.getProperties();
        }

        @Override
        public Query createQuery(String qlString) {
            throw unsupported();
        }

        @Override
        public <T> TypedQuery<T> createQuery(CriteriaQuery<T> criteriaQuery) {
            throw unsupported();
        }

        @Override
        public Query createQuery(CriteriaUpdate updateQuery) {
            throw unsupported();
        }

        @Override
        public Query createQuery(CriteriaDelete deleteQuery) {
            throw unsupported();
        }

        @Override
        public <T> TypedQuery<T> createQuery(String qlString, Class<T> resultClass) {
            throw unsupported();
        }

        @Override
        public Query createNamedQuery(String name) {
            throw unsupported();
        }

        @Override
        public <T> TypedQuery<T> createNamedQuery(String name, Class<T> resultClass) {
            throw unsupported();
        }

        @Override
        public Query createNativeQuery(String sqlString) {
            throw unsupported();
        }

        @Override
        public Query createNativeQuery(String sqlString, Class resultClass) {
            throw unsupported();
        }

        @Override
        public Query createNativeQuery(String sqlString, String resultSetMapping) {
            throw unsupported();
        }

        @Override
        public StoredProcedureQuery createNamedStoredProcedureQuery(String name) {
            throw unsupported();
        }

        @Override
        public StoredProcedureQuery createStoredProcedureQuery(String procedureName) {
            throw unsupported();
        }

        @Override
        public StoredProcedureQuery createStoredProcedureQuery(String procedureName, Class... resultClasses) {
            throw unsupported();
        }

        @Override
        public StoredProcedureQuery createStoredProcedureQuery(String procedureName, String... resultSetMappings) {
            throw unsupported();
        }

        @Override
        public void joinTransaction() {
            throw unsupported();
        }

        @Override
        public boolean isJoinedToTransaction() {
            throw unsupported();
        }

        @Override
        public <T> T unwrap(Class<T> cls) {
            throw new PersistenceException("No provider-specific delegate is available");
        }

        @Override
        public Object getDelegate() {
            return delegate;
        }

        @Override
        public void close() {
            throw unsupported();
        }

        @Override
        public boolean isOpen() {
            return true;
        }

        @Override
        public EntityTransaction getTransaction() {
            throw unsupported();
        }

        @Override
        public EntityManagerFactory getEntityManagerFactory() {
            return entityManagerFactory;
        }

        @Override
        public CriteriaBuilder getCriteriaBuilder() {
            throw unsupported();
        }

        @Override
        public Metamodel getMetamodel() {
            throw unsupported();
        }

        @Override
        public <T> EntityGraph<T> createEntityGraph(Class<T> rootType) {
            throw unsupported();
        }

        @Override
        public EntityGraph<?> createEntityGraph(String graphName) {
            throw unsupported();
        }

        @Override
        public EntityGraph<?> getEntityGraph(String graphName) {
            throw unsupported();
        }

        @Override
        public <T> List<EntityGraph<? super T>> getEntityGraphs(Class<T> entityClass) {
            throw unsupported();
        }
    }

    private static final class StubEntityManagerFactory implements EntityManagerFactory {
        private final Map<String, Object> properties;

        private StubEntityManagerFactory(Map<String, Object> properties) {
            this.properties = Map.copyOf(properties);
        }

        @Override
        public EntityManager createEntityManager() {
            throw unsupported();
        }

        @Override
        public EntityManager createEntityManager(Map map) {
            throw unsupported();
        }

        @Override
        public EntityManager createEntityManager(SynchronizationType synchronizationType) {
            throw unsupported();
        }

        @Override
        public EntityManager createEntityManager(SynchronizationType synchronizationType, Map map) {
            throw unsupported();
        }

        @Override
        public CriteriaBuilder getCriteriaBuilder() {
            throw unsupported();
        }

        @Override
        public Metamodel getMetamodel() {
            throw unsupported();
        }

        @Override
        public boolean isOpen() {
            return true;
        }

        @Override
        public void close() {
            throw unsupported();
        }

        @Override
        public Map<String, Object> getProperties() {
            return properties;
        }

        @Override
        public Cache getCache() {
            throw unsupported();
        }

        @Override
        public PersistenceUnitUtil getPersistenceUnitUtil() {
            throw unsupported();
        }

        @Override
        public void addNamedQuery(String name, Query query) {
            throw unsupported();
        }

        @Override
        public <T> T unwrap(Class<T> cls) {
            throw unsupported();
        }

        @Override
        public <T> void addNamedEntityGraph(String graphName, EntityGraph<T> entityGraph) {
            throw unsupported();
        }
    }

    private static UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException("Only provider detection methods are supported");
    }
}
