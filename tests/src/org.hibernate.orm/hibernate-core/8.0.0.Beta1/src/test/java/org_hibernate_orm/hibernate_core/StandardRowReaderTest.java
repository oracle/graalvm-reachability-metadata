/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_orm.hibernate_core;

import java.util.List;

import org.hibernate.SessionFactory;
import org_hibernate_orm.hibernate_core.entity.Person;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class StandardRowReaderTest {

    @Test
    void multiColumnSelectionReturnsObjectArrayRows() {
        try (SessionFactory sessionFactory = SessionFactoryTestSupport
                .h2Configuration(Person.class)
                .buildSessionFactory()) {
            Person.persistAliceAndBob(sessionFactory);
            List<Object[]> rows = sessionFactory.fromTransaction(session -> session.createSelectionQuery(
                    "select p.id, p.name, p.age from Person p order by p.id",
                    Object[].class).getResultList());

            assertThat(rows).hasSize(2);
            assertThat(rows.get(0)).containsExactly(1L, "Alice", 31);
            assertThat(rows.get(1)).containsExactly(2L, "Bob", 27);
        }
    }
}
