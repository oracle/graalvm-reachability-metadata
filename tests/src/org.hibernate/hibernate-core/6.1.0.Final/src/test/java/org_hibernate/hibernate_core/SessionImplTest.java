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

import static org.assertj.core.api.Assertions.assertThat;

public class SessionImplTest {

    @Test
    public void serializesAndRestoresAConfiguredSession() {
        SessionSerializationTestSupport.serializeSessionWithManagedEntitiesAndCollections();
    }

    @Test
    public void dirtyChecksMergesAndRemovesAnEntityAcrossSessions() {
        try (SessionFactory factory = createSessionFactory("session-lifecycle")) {
            MetamodelRecord record = new MetamodelRecord();
            record.setName("created");
            factory.inTransaction(session -> session.persist(record));
            assertThat(record.getId()).isNotNull();

            factory.inTransaction(session -> {
                MetamodelRecord managed = session.find(MetamodelRecord.class, record.getId());
                assertThat(managed.getName()).isEqualTo("created");
                managed.setName("dirty-checked");
            });

            MetamodelRecord detached = factory.fromTransaction(session ->
                    session.find(MetamodelRecord.class, record.getId()));
            assertThat(detached.getName()).isEqualTo("dirty-checked");
            detached.setName("merged");

            factory.inTransaction(session -> {
                MetamodelRecord merged = session.merge(detached);
                assertThat(merged).isNotSameAs(detached);
                assertThat(merged.getName()).isEqualTo("merged");
                assertThat(session.contains(merged)).isTrue();
                assertThat(session.contains(detached)).isFalse();
            });

            factory.inTransaction(session -> {
                MetamodelRecord reloaded = session.find(MetamodelRecord.class, record.getId());
                assertThat(reloaded.getName()).isEqualTo("merged");
                session.remove(reloaded);
            });

            factory.inTransaction(session ->
                    assertThat(session.find(MetamodelRecord.class, record.getId())).isNull());
        }
    }

    @Test
    public void refreshesBulkUpdatedStateAndRollsBackTheUpdate() {
        try (SessionFactory factory = createSessionFactory("session-refresh")) {
            MetamodelRecord record = new MetamodelRecord();
            record.setName("committed");
            factory.inTransaction(session -> session.persist(record));

            try (Session session = factory.openSession()) {
                Transaction transaction = session.beginTransaction();
                try {
                    MetamodelRecord managed = session.find(MetamodelRecord.class, record.getId());
                    int updated = session.createMutationQuery("""
                                    update StaticMetamodelRecord set name = :name where id = :id
                                    """)
                            .setParameter("name", "bulk-updated")
                            .setParameter("id", record.getId())
                            .executeUpdate();
                    assertThat(updated).isEqualTo(1);
                    assertThat(managed.getName()).isEqualTo("committed");

                    session.refresh(managed);
                    assertThat(managed.getName()).isEqualTo("bulk-updated");
                } finally {
                    transaction.rollback();
                }
            }

            factory.inTransaction(session -> {
                MetamodelRecord reloaded = session.find(MetamodelRecord.class, record.getId());
                assertThat(reloaded.getName()).isEqualTo("committed");
            });
        }
    }

    private static SessionFactory createSessionFactory(String databaseName) {
        return new Configuration()
                .addAnnotatedClass(MetamodelRecord.class)
                .setProperty(AvailableSettings.URL, "jdbc:h2:mem:" + databaseName)
                .setProperty(AvailableSettings.DRIVER, "org.h2.Driver")
                .setProperty(AvailableSettings.DIALECT, "org.hibernate.dialect.H2Dialect")
                .setProperty(AvailableSettings.HBM2DDL_AUTO, "create-drop")
                .setProperty(AvailableSettings.JAKARTA_VALIDATION_MODE, "none")
                .buildSessionFactory();
    }
}
