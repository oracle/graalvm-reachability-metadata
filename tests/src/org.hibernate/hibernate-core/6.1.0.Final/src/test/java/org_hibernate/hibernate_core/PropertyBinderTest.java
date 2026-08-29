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
import org.hibernate.annotations.AttributeAccessor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

public class PropertyBinderTest {

    @Test
    public void appliesAttributeBindingAndValueGenerationAnnotations() {
        Configuration configuration = new Configuration()
                .addAnnotatedClass(GeneratedEntity.class)
                .setProperty(AvailableSettings.URL, "jdbc:h2:mem:property-binder")
                .setProperty(AvailableSettings.DRIVER, "org.h2.Driver")
                .setProperty(AvailableSettings.DIALECT, "org.hibernate.dialect.H2Dialect")
                .setProperty(AvailableSettings.HBM2DDL_AUTO, "create-drop")
                .setProperty(AvailableSettings.JAKARTA_VALIDATION_MODE, "none");

        try (SessionFactory factory = configuration.buildSessionFactory();
                Session session = factory.openSession()) {
            Transaction transaction = session.beginTransaction();
            GeneratedEntity entity = new GeneratedEntity();
            entity.setName("bound");
            session.persist(entity);
            transaction.commit();

            assertThat(entity.getCreatedAt()).isNotNull();
            assertThat(session.find(GeneratedEntity.class, entity.getId()).getName()).isEqualTo("bound");
        }
    }

    @Entity(name = "PropertyBinderGeneratedEntity")
    @Table(name = "PROPERTY_BINDER_GENERATED_ENTITY")
    public static class GeneratedEntity {
        @Id
        @GeneratedValue
        private Long id;

        @AttributeAccessor("field")
        private String name;

        @CreationTimestamp
        private Instant createdAt;

        public Long getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Instant getCreatedAt() {
            return createdAt;
        }
    }
}
