/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_orm.hibernate_core;

import java.util.List;

import org.hibernate.SessionFactory;
import org.hibernate.annotations.Imported;
import org_hibernate_orm.hibernate_core.entity.Person;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BeanInjectorFieldTest {

    @Test
    void aliasedSelectionIsAppliedToFieldsWithoutSetters() {
        try (SessionFactory sessionFactory = SessionFactoryTestSupport
                .h2Configuration(Person.class, AgeEntry.class)
                .buildSessionFactory()) {
            Person.persistAliceAndBob(sessionFactory);
            List<AgeEntry> entries = sessionFactory.fromTransaction(session -> session.createSelectionQuery(
                    "select new AgeEntry(p.name as owner, p.age as years) from Person p order by p.age desc",
                    AgeEntry.class).getResultList());

            assertThat(entries).extracting(entry -> entry.owner + "=" + entry.years)
                    .containsExactly("Alice=31", "Bob=27");
        }
    }

    @Imported(rename = "AgeEntry")
    public static class AgeEntry {

        public String owner;

        public Integer years;
    }
}
