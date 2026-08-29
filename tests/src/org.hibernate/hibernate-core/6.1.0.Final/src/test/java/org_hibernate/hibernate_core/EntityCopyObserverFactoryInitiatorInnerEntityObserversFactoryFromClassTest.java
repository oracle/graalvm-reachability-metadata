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
import org.hibernate.event.spi.EntityCopyObserver;
import org.hibernate.event.spi.EventSource;
import org.junit.jupiter.api.Test;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class EntityCopyObserverFactoryInitiatorInnerEntityObserversFactoryFromClassTest {

    @Test
    public void createsAConfiguredObserverForEachMerge() {
        RecordingObserver.instances.set(0);
        Configuration configuration = new Configuration()
                .addAnnotatedClass(MergeRecord.class)
                .setProperty(AvailableSettings.URL, "jdbc:h2:mem:entity-copy-observer")
                .setProperty(AvailableSettings.DRIVER, "org.h2.Driver")
                .setProperty(AvailableSettings.DIALECT, "org.hibernate.dialect.H2Dialect")
                .setProperty(AvailableSettings.HBM2DDL_AUTO, "create-drop")
                .setProperty(AvailableSettings.JAKARTA_VALIDATION_MODE, "none")
                .setProperty(
                        AvailableSettings.MERGE_ENTITY_COPY_OBSERVER,
                        RecordingObserver.class.getName()
                );

        try (SessionFactory factory = configuration.buildSessionFactory();
                Session session = factory.openSession()) {
            Transaction transaction = session.beginTransaction();
            MergeRecord merged = (MergeRecord) session.merge(new MergeRecord("merged"));
            transaction.commit();

            assertThat(merged.getId()).isNotNull();
            assertThat(merged.getName()).isEqualTo("merged");
            assertThat(RecordingObserver.instances.get()).isGreaterThanOrEqualTo(2);
        }
    }

    public static class RecordingObserver implements EntityCopyObserver {
        private static final AtomicInteger instances = new AtomicInteger();

        public RecordingObserver() {
            instances.incrementAndGet();
        }

        @Override
        public void entityCopyDetected(
                Object managedEntity,
                Object mergeEntity1,
                Object mergeEntity2,
                EventSource session) {
        }

        @Override
        public void topLevelMergeComplete(EventSource session) {
        }

        @Override
        public void clear() {
        }
    }

    @Entity(name = "MergeRecord")
    @Table(name = "MERGE_RECORD")
    public static class MergeRecord {
        @Id
        @GeneratedValue
        private Long id;

        private String name;

        public MergeRecord() {
        }

        public MergeRecord(String name) {
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
