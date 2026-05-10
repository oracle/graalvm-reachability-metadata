/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_orm;

import java.util.Collections;
import java.util.Map;

import javax.persistence.Cache;
import javax.persistence.EntityGraph;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceUnitUtil;
import javax.persistence.Query;
import javax.persistence.SynchronizationType;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.metamodel.Metamodel;

import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.orm.jpa.vendor.HibernateJpaSessionFactoryBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings({"deprecation", "rawtypes"})
public class HibernateJpaSessionFactoryBeanTest implements EntityManagerFactory {
    private int getSessionFactoryCalls;

    @Test
    void getObjectReflectivelyInvokesGetSessionFactoryOnConfiguredEntityManagerFactory() {
        HibernateJpaSessionFactoryBean factoryBean = new HibernateJpaSessionFactoryBean();
        factoryBean.setEntityManagerFactory(this);

        SessionFactory sessionFactory = factoryBean.getObject();

        assertNull(sessionFactory);
        assertEquals(1, this.getSessionFactoryCalls);
        assertSame(SessionFactory.class, factoryBean.getObjectType());
        assertTrue(factoryBean.isSingleton());
    }

    public SessionFactory getSessionFactory() {
        this.getSessionFactoryCalls++;
        return null;
    }

    @Override
    public EntityManager createEntityManager() {
        throw new UnsupportedOperationException("EntityManager creation is not used by this test");
    }

    @Override
    public EntityManager createEntityManager(Map map) {
        throw new UnsupportedOperationException("EntityManager creation is not used by this test");
    }

    @Override
    public EntityManager createEntityManager(SynchronizationType synchronizationType) {
        throw new UnsupportedOperationException("EntityManager creation is not used by this test");
    }

    @Override
    public EntityManager createEntityManager(SynchronizationType synchronizationType, Map map) {
        throw new UnsupportedOperationException("EntityManager creation is not used by this test");
    }

    @Override
    public CriteriaBuilder getCriteriaBuilder() {
        throw new UnsupportedOperationException("CriteriaBuilder access is not used by this test");
    }

    @Override
    public Metamodel getMetamodel() {
        throw new UnsupportedOperationException("Metamodel access is not used by this test");
    }

    @Override
    public boolean isOpen() {
        return true;
    }

    @Override
    public void close() {
        throw new UnsupportedOperationException("Close is not used by this test");
    }

    @Override
    public Map<String, Object> getProperties() {
        return Collections.emptyMap();
    }

    @Override
    public Cache getCache() {
        throw new UnsupportedOperationException("Cache access is not used by this test");
    }

    @Override
    public PersistenceUnitUtil getPersistenceUnitUtil() {
        throw new UnsupportedOperationException("PersistenceUnitUtil access is not used by this test");
    }

    @Override
    public void addNamedQuery(String name, Query query) {
        throw new UnsupportedOperationException("Named query registration is not used by this test");
    }

    @Override
    public <T> T unwrap(Class<T> cls) {
        throw new UnsupportedOperationException("Unwrap is not used by this test");
    }

    @Override
    public <T> void addNamedEntityGraph(String graphName, EntityGraph<T> entityGraph) {
        throw new UnsupportedOperationException("Named entity graph registration is not used by this test");
    }
}
