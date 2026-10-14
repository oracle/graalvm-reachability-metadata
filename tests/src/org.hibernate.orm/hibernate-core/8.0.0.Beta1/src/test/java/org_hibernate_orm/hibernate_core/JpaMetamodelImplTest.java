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

public class JpaMetamodelImplTest {

    public static final int AGE_LIMIT = 30;

    @Test
    void queryReferencesAJavaConstantByItsQualifiedName() {
        try (SessionFactory sessionFactory = SessionFactoryTestSupport
                .h2Configuration(Person.class)
                .buildSessionFactory()) {
            Person.persistAliceAndBob(sessionFactory);
            List<String> names = sessionFactory.fromTransaction(session -> session.createSelectionQuery(
                    "select p.name from Person p where p.age > org_hibernate_orm.hibernate_core.JpaMetamodelImplTest.AGE_LIMIT",
                    String.class).getResultList());

            assertThat(names).containsExactly("Alice");
        }
    }
}
