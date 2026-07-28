/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.dialect.H2Dialect;
import org.junit.jupiter.api.Test;
import org_hibernate.hibernate_core.entity.DialectOverrideEntity;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class AnnotationBinderTest {

    @Test
    public void appliesDialectSpecificAnnotationsDuringEntityBootstrap() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("jakarta.persistence.jdbc.url", "jdbc:h2:mem:dialect-override;DB_CLOSE_DELAY=-1");
        properties.put("hibernate.dialect", H2Dialect.class.getName());
        properties.put("hibernate.hbm2ddl.auto", "create-drop");
        properties.put("hibernate.bytecode.provider", "none");

        EntityManagerFactory entityManagerFactory = Persistence.createEntityManagerFactory(
                "DialectOverridePU",
                properties
        );
        try {
            EntityManager entityManager = entityManagerFactory.createEntityManager();
            try {
                entityManager.getTransaction().begin();
                entityManager.persist(new DialectOverrideEntity("active", true));
                entityManager.persist(new DialectOverrideEntity("inactive", false));
                entityManager.getTransaction().commit();
                entityManager.clear();

                List<DialectOverrideEntity> entities = entityManager.createQuery(
                                "from DialectOverrideEntity order by name",
                                DialectOverrideEntity.class
                        )
                        .getResultList();

                assertThat(entities)
                        .extracting(DialectOverrideEntity::getName)
                        .containsExactly("active");
                assertThat(entities).allMatch(DialectOverrideEntity::isActive);
            }
            finally {
                entityManager.close();
            }
        }
        finally {
            entityManagerFactory.close();
        }
    }
}
