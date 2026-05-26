/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.DialectOverride;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.dialect.H2Dialect;
import org.junit.jupiter.api.Test;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class AnnotationBinderTest {

    @Test
    public void appliesDialectOverrideDeclaredInRepeatableContainer() {
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting(AvailableSettings.DIALECT, H2Dialect.class.getName())
                .applySetting(AvailableSettings.DRIVER, "org.h2.Driver")
                .applySetting(AvailableSettings.URL, "jdbc:h2:mem:annotation_binder_"
                        + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1")
                .applySetting(AvailableSettings.HBM2DDL_AUTO, "create-drop")
                .applySetting(AvailableSettings.SHOW_SQL, "false")
                .build();
        SessionFactory sessionFactory = null;
        try {
            sessionFactory = new MetadataSources(registry)
                    .addAnnotatedClass(DialectOverrideEntity.class)
                    .buildMetadata()
                    .buildSessionFactory();
            persistEntityMatchingH2Override(sessionFactory);
            assertThat(loadEntity(sessionFactory).getScore()).isEqualTo(50);
        }
        finally {
            if (sessionFactory != null) {
                sessionFactory.close();
            }
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    private static void persistEntityMatchingH2Override(SessionFactory sessionFactory) {
        try (Session session = sessionFactory.openSession()) {
            Transaction transaction = session.beginTransaction();
            session.persist(new DialectOverrideEntity(1L, 50));
            transaction.commit();
        }
    }

    private static DialectOverrideEntity loadEntity(SessionFactory sessionFactory) {
        try (Session session = sessionFactory.openSession()) {
            return session.find(DialectOverrideEntity.class, 1L);
        }
    }

    @Entity(name = "DialectOverrideEntity")
    @Table(name = "annotation_binder_override_entity")
    @Check(constraints = "score > 1000")
    @DialectOverride.Checks({
            @DialectOverride.Check(
                    dialect = H2Dialect.class,
                    override = @Check(constraints = "score < 100"))
    })
    public static class DialectOverrideEntity {

        @Id
        private Long id;

        private int score;

        public DialectOverrideEntity() {
        }

        public DialectOverrideEntity(Long id, int score) {
            this.id = id;
            this.score = score;
        }

        public int getScore() {
            return score;
        }
    }
}
