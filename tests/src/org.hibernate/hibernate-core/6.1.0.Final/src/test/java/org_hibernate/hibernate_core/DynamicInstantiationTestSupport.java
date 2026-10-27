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

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public final class DynamicInstantiationTestSupport {
    private static final AtomicInteger DATABASE_SEQUENCE = new AtomicInteger();

    private DynamicInstantiationTestSupport() {
    }

    public static void projectUsingAConstructor() {
        try (SessionFactory factory = createSessionFactory();
                Session session = factory.openSession()) {
            persistRecord(session);

            ConstructorProjection projection = session.createQuery(
                            "select new org_hibernate.hibernate_core.ConstructorProjection(r.name) "
                                    + "from StaticMetamodelRecord r",
                            ConstructorProjection.class
                    )
                    .getSingleResult();

            assertThat(projection.getName()).isEqualTo("projected");
        }
    }

    public static void projectUsingAField() {
        try (SessionFactory factory = createSessionFactory();
                Session session = factory.openSession()) {
            persistRecord(session);

            FieldProjection projection = session.createQuery(
                            "select new org_hibernate.hibernate_core.FieldProjection(r.name as name) "
                                    + "from StaticMetamodelRecord r",
                            FieldProjection.class
                    )
                    .getSingleResult();

            assertThat(projection.name).isEqualTo("projected");
        }
    }

    public static void projectUsingASetter() {
        try (SessionFactory factory = createSessionFactory();
                Session session = factory.openSession()) {
            persistRecord(session);

            SetterProjection projection = session.createQuery(
                            "select new org_hibernate.hibernate_core.SetterProjection(r.name as name) "
                                    + "from StaticMetamodelRecord r",
                            SetterProjection.class
                    )
                    .getSingleResult();

            assertThat(projection.getName()).isEqualTo("projected");
        }
    }

    private static SessionFactory createSessionFactory() {
        return new Configuration()
                .addAnnotatedClass(MetamodelRecord.class)
                .setProperty(
                        AvailableSettings.URL,
                        "jdbc:h2:mem:dynamic-instantiation-" + DATABASE_SEQUENCE.incrementAndGet()
                )
                .setProperty(AvailableSettings.DRIVER, "org.h2.Driver")
                .setProperty(AvailableSettings.DIALECT, "org.hibernate.dialect.H2Dialect")
                .setProperty(AvailableSettings.HBM2DDL_AUTO, "create-drop")
                .setProperty(AvailableSettings.JAKARTA_VALIDATION_MODE, "none")
                .buildSessionFactory();
    }

    private static void persistRecord(Session session) {
        Transaction transaction = session.beginTransaction();
        MetamodelRecord record = new MetamodelRecord();
        record.setName("projected");
        session.persist(record);
        transaction.commit();
    }
}
