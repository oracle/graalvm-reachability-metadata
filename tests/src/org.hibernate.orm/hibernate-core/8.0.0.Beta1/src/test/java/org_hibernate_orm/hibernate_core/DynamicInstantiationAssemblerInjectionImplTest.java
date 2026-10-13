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

public class DynamicInstantiationAssemblerInjectionImplTest {

    @Test
    void selectNewWithAliasesInjectsIntoADefaultConstructedBean() {
        try (SessionFactory sessionFactory = SessionFactoryTestSupport
                .h2Configuration(Person.class, PersonCard.class)
                .buildSessionFactory()) {
            Person.persistAliceAndBob(sessionFactory);
            List<PersonCard> cards = sessionFactory.fromTransaction(session -> session.createSelectionQuery(
                    "select new PersonCard(p.name as name, p.age as age) from Person p order by p.name",
                    PersonCard.class).getResultList());

            assertThat(cards).extracting(PersonCard::describe).containsExactly("Alice (31)", "Bob (27)");
        }
    }

    @Imported(rename = "PersonCard")
    public static class PersonCard {

        private String name;

        private Integer age;

        public void setName(String name) {
            this.name = name;
        }

        public void setAge(Integer age) {
            this.age = age;
        }

        String describe() {
            return name + " (" + age + ")";
        }
    }
}
