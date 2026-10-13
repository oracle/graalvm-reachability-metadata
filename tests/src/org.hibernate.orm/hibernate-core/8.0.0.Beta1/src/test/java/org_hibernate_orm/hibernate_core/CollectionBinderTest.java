/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_orm.hibernate_core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import org.hibernate.SessionFactory;
import org.hibernate.annotations.SortComparator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CollectionBinderTest {

    @Test
    void sortComparatorAnnotationBindsTheConfiguredComparator() {
        try (SessionFactory sessionFactory = SessionFactoryTestSupport
                .h2Configuration(Glossary.class)
                .buildSessionFactory()) {
            sessionFactory.inTransaction(session -> session.persist(new Glossary(1L, List.of("kiwi", "fig", "banana"))));
            List<String> terms = sessionFactory.fromTransaction(session -> new ArrayList<>(
                    session.find(Glossary.class, 1L).getTerms()));

            assertThat(terms).containsExactly("fig", "kiwi", "banana");
        }
    }

    public static class LengthComparator implements Comparator<String> {

        @Override
        public int compare(String first, String second) {
            int byLength = Integer.compare(first.length(), second.length());
            return byLength != 0 ? byLength : first.compareTo(second);
        }
    }

    @Entity(name = "Glossary")
    public static class Glossary {

        @Id
        private Long id;

        @ElementCollection(fetch = FetchType.EAGER)
        @SortComparator(LengthComparator.class)
        private SortedSet<String> terms = new TreeSet<>(new LengthComparator());

        public Glossary() {
        }

        public Glossary(Long id, List<String> terms) {
            this.id = id;
            this.terms.addAll(terms);
        }

        public SortedSet<String> getTerms() {
            return terms;
        }
    }
}
