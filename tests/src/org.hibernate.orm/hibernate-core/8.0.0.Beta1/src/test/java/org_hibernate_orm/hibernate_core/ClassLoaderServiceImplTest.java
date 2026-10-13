/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_orm.hibernate_core;

import java.util.List;

import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org_hibernate_orm.hibernate_core.entity.Person;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassLoaderServiceImplTest {

    @Test
    void configurationResourceAndAnnotatedPackageAreLoadedFromTheClasspath() {
        try (SessionFactory sessionFactory = new Configuration()
                .configure("org_hibernate_orm/hibernate_core/people.cfg.xml")
                .buildSessionFactory()) {
            Person.persistAliceAndBob(sessionFactory);
            List<String> names = sessionFactory.fromTransaction(session -> session
                    .createNamedSelectionQuery("Person.olderThan", String.class)
                    .setParameter("age", 28)
                    .getResultList());

            assertThat(names).containsExactly("Alice");
        }
    }
}
