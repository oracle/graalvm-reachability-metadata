/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.DialectOverride;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.Configuration;
import org.hibernate.dialect.H2Dialect;
import org.junit.jupiter.api.Test;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import static org.assertj.core.api.Assertions.assertThat;

public class AnnotationBinderTest {

    @Test
    public void appliesDialectSpecificCheckAnnotationDuringEntityBinding() {
        try (SessionFactory factory = new Configuration()
                .addAnnotatedClass(DialectCheckedEntity.class)
                .setProperty(AvailableSettings.URL, "jdbc:h2:mem:annotation-binder")
                .setProperty(AvailableSettings.DRIVER, "org.h2.Driver")
                .setProperty(AvailableSettings.DIALECT, H2Dialect.class.getName())
                .setProperty(AvailableSettings.HBM2DDL_AUTO, "create-drop")
                .buildSessionFactory();
                Session session = factory.openSession()) {
            session.beginTransaction();
            DialectCheckedEntity entity = new DialectCheckedEntity();
            entity.id = 1L;
            entity.name = "bound";
            session.persist(entity);
            session.getTransaction().commit();

            assertThat(session.find(DialectCheckedEntity.class, 1L).name).isEqualTo("bound");
        }
    }

    @Entity(name = "DialectCheckedEntity")
    @Check(constraints = "name is not null")
    @DialectOverride.Check(
            dialect = H2Dialect.class,
            override = @Check(constraints = "name is not null")
    )
    @DialectOverride.Check(
            dialect = H2Dialect.class,
            override = @Check(constraints = "name is not null")
    )
    public static class DialectCheckedEntity {
        @Id
        private Long id;

        private String name;
    }
}
