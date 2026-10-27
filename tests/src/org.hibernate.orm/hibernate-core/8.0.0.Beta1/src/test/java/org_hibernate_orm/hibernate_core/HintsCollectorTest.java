/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_orm.hibernate_core;

import java.util.List;
import java.util.Set;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.hibernate.SessionFactory;
import org.hibernate.jpa.AvailableHints;
import org.hibernate.jpa.HibernateHints;
import org.hibernate.jpa.SpecHints;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class HintsCollectorTest {

    @Test
    void definedHintsIncludeHibernateAndSpecificationHints() {
        Set<String> hints = AvailableHints.getDefinedHints();

        assertThat(hints).contains(
                HibernateHints.HINT_READ_ONLY,
                HibernateHints.HINT_FETCH_SIZE,
                SpecHints.HINT_SPEC_QUERY_TIMEOUT);

        try (SessionFactory sessionFactory = SessionFactoryTestSupport
                .h2Configuration(Memo.class)
                .buildSessionFactory()) {
            sessionFactory.inTransaction(session -> {
                session.persist(new Memo(1L, "first"));
                session.persist(new Memo(2L, "second"));
            });
            List<Memo> memos = sessionFactory.fromTransaction(session -> session
                    .createSelectionQuery("from Memo m order by m.id", Memo.class)
                    .setHint(HibernateHints.HINT_READ_ONLY, true)
                    .getResultList());

            assertThat(memos).extracting(Memo::getLabel).containsExactly("first", "second");
        }
    }

    @Entity(name = "Memo")
    public static class Memo {

        @Id
        private Long id;

        private String label;

        public Memo() {
        }

        public Memo(Long id, String label) {
            this.id = id;
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }
}
