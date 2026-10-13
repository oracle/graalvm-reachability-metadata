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

public class NativeQueryConstructorTransformerTest {

    @Test
    void nativeQueryResultRowsAreConstructedIntoTheResultClass() {
        try (SessionFactory sessionFactory = SessionFactoryTestSupport
                .h2Configuration(Person.class)
                .buildSessionFactory()) {
            Person.persistAliceAndBob(sessionFactory);
            List<Badge> badges = sessionFactory.fromTransaction(session -> session.createNativeQuery(
                    "select name, age from Person order by age", Badge.class).getResultList());

            assertThat(badges).containsExactly(new Badge("Bob", 27), new Badge("Alice", 31));
        }
    }

    public record Badge(String name, Integer age) {
    }
}
