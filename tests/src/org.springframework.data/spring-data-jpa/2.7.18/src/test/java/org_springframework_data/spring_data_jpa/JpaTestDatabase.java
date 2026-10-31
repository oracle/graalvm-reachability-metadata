/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_data.spring_data_jpa;

import java.util.UUID;

import javax.persistence.EntityManager;

import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;

final class JpaTestDatabase implements AutoCloseable {
    private final SessionFactory factory;
    private final EntityManager entityManager;
    private final SimpleJpaRepository<CatalogItem, Long> repository;

    JpaTestDatabase() {
        factory = new Configuration()
                .addAnnotatedClass(CatalogItem.class)
                .setProperty("hibernate.connection.driver_class", "org.h2.Driver")
                .setProperty("hibernate.connection.url", "jdbc:h2:mem:catalog-" + UUID.randomUUID() + ";LOCK_TIMEOUT=10000")
                .setProperty("hibernate.dialect", "org.hibernate.dialect.H2Dialect")
                .setProperty("hibernate.hbm2ddl.auto", "create-drop")
                .setProperty("javax.persistence.validation.mode", "none")
                .buildSessionFactory();
        entityManager = factory.createEntityManager();
        entityManager.getTransaction().begin();
        repository = new SimpleJpaRepository<>(CatalogItem.class, entityManager);
    }

    EntityManager entityManager() {
        return entityManager;
    }

    SimpleJpaRepository<CatalogItem, Long> repository() {
        return repository;
    }

    @Override
    public void close() {
        try {
            if (entityManager.getTransaction().isActive()) {
                entityManager.getTransaction().rollback();
            }
        } finally {
            try {
                entityManager.close();
            } finally {
                factory.close();
            }
        }
    }
}
