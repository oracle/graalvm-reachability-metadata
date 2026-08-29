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
import org.hibernate.annotations.SortComparator;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.Comparator;
import java.util.SortedSet;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

public class CollectionBinderTest {

    @Test
    public void instantiatesAndAppliesACollectionSortComparator() {
        Configuration configuration = new Configuration()
                .addAnnotatedClass(SortedRecord.class)
                .setProperty(AvailableSettings.URL, "jdbc:h2:mem:collection-sorter")
                .setProperty(AvailableSettings.DRIVER, "org.h2.Driver")
                .setProperty(AvailableSettings.DIALECT, "org.hibernate.dialect.H2Dialect")
                .setProperty(AvailableSettings.HBM2DDL_AUTO, "create-drop")
                .setProperty(AvailableSettings.JAKARTA_VALIDATION_MODE, "none");

        try (SessionFactory factory = configuration.buildSessionFactory();
                Session session = factory.openSession()) {
            Transaction transaction = session.beginTransaction();
            SortedRecord record = new SortedRecord();
            record.getValues().add("alpha");
            record.getValues().add("omega");
            session.persist(record);
            transaction.commit();
            session.clear();

            SortedRecord loaded = session.find(SortedRecord.class, record.getId());

            assertThat(loaded.getValues()).containsExactly("omega", "alpha");
        }
    }

    @Entity(name = "SortedCollectionRecord")
    @Table(name = "SORTED_COLLECTION_RECORD")
    public static class SortedRecord {
        @Id
        @GeneratedValue
        private Long id;

        @ElementCollection(fetch = FetchType.EAGER)
        @CollectionTable(
                name = "SORTED_COLLECTION_VALUE",
                joinColumns = @JoinColumn(name = "record_id")
        )
        @Column(name = "sorted_value")
        @SortComparator(DescendingComparator.class)
        private SortedSet<String> values = new TreeSet<>(new DescendingComparator());

        public Long getId() {
            return id;
        }

        public SortedSet<String> getValues() {
            return values;
        }
    }

    public static class DescendingComparator implements Comparator<String> {
        @Override
        public int compare(String first, String second) {
            return second.compareTo(first);
        }
    }
}
