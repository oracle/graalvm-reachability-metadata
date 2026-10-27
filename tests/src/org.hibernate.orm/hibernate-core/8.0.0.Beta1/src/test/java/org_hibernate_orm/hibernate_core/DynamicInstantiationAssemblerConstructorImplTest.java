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

public class DynamicInstantiationAssemblerConstructorImplTest {

    @Test
    void selectNewInvokesTheMatchingConstructor() {
        try (SessionFactory sessionFactory = SessionFactoryTestSupport
                .h2Configuration(Person.class, PersonSummary.class)
                .buildSessionFactory()) {
            Person.persistAliceAndBob(sessionFactory);
            List<PersonSummary> summaries = sessionFactory.fromTransaction(session -> session.createSelectionQuery(
                    "select new PersonSummary(p.name, p.age) from Person p order by p.age",
                    PersonSummary.class).getResultList());

            assertThat(summaries).containsExactly(new PersonSummary("Bob", 27), new PersonSummary("Alice", 31));
        }
    }

    @Imported(rename = "PersonSummary")
    public record PersonSummary(String name, Integer age) {
    }
}
