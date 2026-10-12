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

public class RowTransformerConstructorImplTest {

    @Test
    void typedSelectionQueryPassesEachRowToTheResultConstructor() {
        try (SessionFactory sessionFactory = SessionFactoryTestSupport
                .h2Configuration(Person.class)
                .buildSessionFactory()) {
            Person.persistAliceAndBob(sessionFactory);
            List<Greeting> greetings = sessionFactory.fromTransaction(session -> session.createSelectionQuery(
                    "select p.name, p.age from Person p order by p.name",
                    Greeting.class).getResultList());

            assertThat(greetings).extracting(Greeting::text).containsExactly("Hi Alice, 31", "Hi Bob, 27");
        }
    }

    public record Greeting(String text) {

        public Greeting(String name, Integer age) {
            this("Hi " + name + ", " + age);
        }
    }
}
