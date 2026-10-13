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

public class SqmUtilTest {

    @Test
    void singleSelectionIsWrappedByASingleArgumentConstructor() {
        try (SessionFactory sessionFactory = SessionFactoryTestSupport
                .h2Configuration(Person.class)
                .buildSessionFactory()) {
            Person.persistAliceAndBob(sessionFactory);
            List<Initial> initials = sessionFactory.fromTransaction(session -> session.createSelectionQuery(
                    "select p.name from Person p order by p.name", Initial.class).getResultList());

            assertThat(initials).extracting(Initial::letter).containsExactly('A', 'B');
        }
    }

    public record Initial(char letter) {

        public Initial(String name) {
            this(name.charAt(0));
        }
    }
}
