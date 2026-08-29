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
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.NaturalId;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.Configuration;
import org.hibernate.internal.util.SerializationHelper;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public final class SessionSerializationTestSupport {
    private static final AtomicInteger DATABASE_SEQUENCE = new AtomicInteger();

    private SessionSerializationTestSupport() {
    }

    public static void serializeSessionWithManagedEntitiesAndCollections() {
        Configuration configuration = new Configuration()
                .addAnnotatedClass(MutableRecord.class)
                .addAnnotatedClass(ImmutableRecord.class)
                .addAnnotatedClass(RecordReference.class)
                .setProperty(
                        AvailableSettings.URL,
                        "jdbc:h2:mem:session-serialization-" + DATABASE_SEQUENCE.incrementAndGet()
                )
                .setProperty(AvailableSettings.DRIVER, "org.h2.Driver")
                .setProperty(AvailableSettings.DIALECT, "org.hibernate.dialect.H2Dialect")
                .setProperty(AvailableSettings.HBM2DDL_AUTO, "create-drop")
                .setProperty(AvailableSettings.JAKARTA_VALIDATION_MODE, "none");

        try (SessionFactory factory = configuration.buildSessionFactory();
                Session session = factory.openSession()) {
            Long referenceId = persistRecords(session);
            session.clear();

            RecordReference reference = session.find(RecordReference.class, referenceId);
            assertThat(reference.getRecord().getCode()).isEqualTo("mutable");
            assertThat(reference.getRecord().getLabels()).containsExactly("metadata", "native");
            assertThat(session.find(ImmutableRecord.class, 1L).getValue()).isEqualTo("immutable");

            try (Session copy = (Session) SerializationHelper.clone(session)) {
                RecordReference copiedReference = copy.find(RecordReference.class, referenceId);
                assertThat(copiedReference.getRecord().getCode()).isEqualTo("mutable");
                assertThat(copiedReference.getRecord().getLabels())
                        .containsExactly("metadata", "native");
            }
        }
    }

    private static Long persistRecords(Session session) {
        Transaction transaction = session.beginTransaction();
        MutableRecord record = new MutableRecord();
        record.setCode("mutable");
        record.getLabels().add("metadata");
        record.getLabels().add("native");
        session.persist(record);

        ImmutableRecord immutableRecord = new ImmutableRecord();
        immutableRecord.setId(1L);
        immutableRecord.setValue("immutable");
        session.persist(immutableRecord);

        RecordReference reference = new RecordReference();
        reference.setRecord(record);
        session.persist(reference);
        transaction.commit();
        return reference.getId();
    }

    @Entity(name = "SerializationMutableRecord")
    @Table(name = "SERIALIZATION_MUTABLE_RECORD")
    public static class MutableRecord implements Serializable {
        private static final long serialVersionUID = 1L;

        @Id
        @GeneratedValue
        private Long id;

        @NaturalId
        @Column(unique = true, nullable = false)
        private String code;

        @ElementCollection(fetch = FetchType.EAGER)
        @CollectionTable(
                name = "SERIALIZATION_MUTABLE_LABEL",
                joinColumns = @JoinColumn(name = "record_id")
        )
        @Column(name = "label_value")
        private List<String> labels = new ArrayList<>();

        public Long getId() {
            return id;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public List<String> getLabels() {
            return labels;
        }
    }

    @Entity(name = "SerializationImmutableRecord")
    @Table(name = "SERIALIZATION_IMMUTABLE_RECORD")
    @Immutable
    public static class ImmutableRecord implements Serializable {
        private static final long serialVersionUID = 1L;

        @Id
        private Long id;

        @Column(name = "record_value")
        private String value;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }

    @Entity(name = "SerializationRecordReference")
    @Table(name = "SERIALIZATION_RECORD_REFERENCE")
    public static class RecordReference implements Serializable {
        private static final long serialVersionUID = 1L;

        @Id
        @GeneratedValue
        private Long id;

        @ManyToOne(fetch = FetchType.EAGER)
        @JoinColumn(name = "record_code", referencedColumnName = "code")
        private MutableRecord record;

        public Long getId() {
            return id;
        }

        public MutableRecord getRecord() {
            return record;
        }

        public void setRecord(MutableRecord record) {
            this.record = record;
        }
    }
}
