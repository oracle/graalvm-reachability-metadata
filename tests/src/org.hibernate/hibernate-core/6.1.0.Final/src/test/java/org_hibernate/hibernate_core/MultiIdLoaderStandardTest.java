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
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class MultiIdLoaderStandardTest {

    @Test
    public void loadsOnlyIdentifiersNotAlreadyManagedByTheSession() {
        Configuration configuration = new Configuration()
                .addAnnotatedClass(MultiLoadRecord.class)
                .setProperty(AvailableSettings.URL, "jdbc:h2:mem:multi-id-loader")
                .setProperty(AvailableSettings.DRIVER, "org.h2.Driver")
                .setProperty(AvailableSettings.DIALECT, "org.hibernate.dialect.H2Dialect")
                .setProperty(AvailableSettings.HBM2DDL_AUTO, "create-drop")
                .setProperty(AvailableSettings.JAKARTA_VALIDATION_MODE, "none");

        try (SessionFactory factory = configuration.buildSessionFactory();
                Session session = factory.openSession()) {
            Transaction transaction = session.beginTransaction();
            session.persist(new MultiLoadRecord(1L, "managed"));
            session.persist(new MultiLoadRecord(2L, "database"));
            transaction.commit();
            session.clear();

            MultiLoadRecord managed = session.find(MultiLoadRecord.class, 1L);
            List<MultiLoadRecord> records = session.byMultipleIds(MultiLoadRecord.class)
                    .enableSessionCheck(true)
                    .enableOrderedReturn(false)
                    .multiLoad(1L, 2L);

            assertThat(records).extracting(MultiLoadRecord::getName)
                    .containsExactlyInAnyOrder("managed", "database");
            assertThat(records).contains(managed);
        }
    }

    @Entity(name = "MultiLoadRecord")
    @Table(name = "MULTI_LOAD_RECORD")
    public static class MultiLoadRecord {
        @Id
        private Long id;

        private String name;

        public MultiLoadRecord() {
        }

        public MultiLoadRecord(Long id, String name) {
            this.id = id;
            this.name = name;
        }

        public Long getId() {
            return id;
        }

        public String getName() {
            return name;
        }
    }
}
