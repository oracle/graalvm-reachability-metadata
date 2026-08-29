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
import org.hibernate.annotations.GenerationTime;
import org.hibernate.annotations.GeneratorType;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.Configuration;
import org.hibernate.tuple.ValueGenerator;
import org.junit.jupiter.api.Test;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("deprecation")
public class VmValueGenerationTest {

    @Test
    public void generatesAPropertyValueWhenAnEntityIsInserted() {
        Configuration configuration = new Configuration()
                .addAnnotatedClass(GeneratedRecord.class)
                .setProperty(AvailableSettings.URL, "jdbc:h2:mem:vm-value-generation")
                .setProperty(AvailableSettings.DRIVER, "org.h2.Driver")
                .setProperty(AvailableSettings.DIALECT, "org.hibernate.dialect.H2Dialect")
                .setProperty(AvailableSettings.HBM2DDL_AUTO, "create-drop")
                .setProperty(AvailableSettings.JAKARTA_VALIDATION_MODE, "none");

        try (SessionFactory factory = configuration.buildSessionFactory();
                Session session = factory.openSession()) {
            Transaction transaction = session.beginTransaction();
            GeneratedRecord record = new GeneratedRecord();
            session.persist(record);
            transaction.commit();

            assertThat(record.getGeneratedValue()).isEqualTo("generated-by-hibernate");
        }
    }

    public static class FixedValueGenerator implements ValueGenerator<String> {
        @Override
        public String generateValue(Session session, Object owner) {
            return "generated-by-hibernate";
        }
    }

    @Entity(name = "GeneratedRecord")
    @Table(name = "GENERATED_RECORD")
    public static class GeneratedRecord {
        @Id
        @GeneratedValue
        private Long id;

        @GeneratorType(type = FixedValueGenerator.class, when = GenerationTime.INSERT)
        private String generatedValue;

        public Long getId() {
            return id;
        }

        public String getGeneratedValue() {
            return generatedValue;
        }
    }
}
