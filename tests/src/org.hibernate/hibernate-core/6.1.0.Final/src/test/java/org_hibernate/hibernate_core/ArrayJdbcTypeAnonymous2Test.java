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
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import static org.assertj.core.api.Assertions.assertThat;

public class ArrayJdbcTypeAnonymous2Test {

    @Test
    public void persistsAndLoadsABasicArrayThroughJdbcArrayBinding() {
        Configuration configuration = new Configuration()
                .addAnnotatedClass(ArrayRecord.class)
                .setProperty(AvailableSettings.URL, "jdbc:h2:mem:jdbc-array-binding")
                .setProperty(AvailableSettings.DRIVER, "org.h2.Driver")
                .setProperty(AvailableSettings.DIALECT, "org.hibernate.dialect.H2Dialect")
                .setProperty(AvailableSettings.HBM2DDL_AUTO, "create-drop")
                .setProperty(AvailableSettings.JAKARTA_VALIDATION_MODE, "none");

        try (SessionFactory factory = configuration.buildSessionFactory()) {
            Long id;
            try (Session session = factory.openSession()) {
                Transaction transaction = session.beginTransaction();
                ArrayRecord record = new ArrayRecord();
                record.setTags(new String[]{"metadata", "native"});
                session.persist(record);
                transaction.commit();
                id = record.getId();
            }

            try (Session session = factory.openSession()) {
                assertThat(session.find(ArrayRecord.class, id).getTags())
                        .containsExactly("metadata", "native");
            }
        }
    }

    @Entity(name = "JdbcArrayRecord")
    @Table(name = "JDBC_ARRAY_RECORD")
    public static class ArrayRecord {
        @Id
        @GeneratedValue
        private Long id;

        private String[] tags;

        public Long getId() {
            return id;
        }

        public String[] getTags() {
            return tags;
        }

        public void setTags(String[] tags) {
            this.tags = tags;
        }
    }
}
