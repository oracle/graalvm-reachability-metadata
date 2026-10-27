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

public class BeanInjectorSetterTest {

    @Test
    void aliasedSelectionIsAppliedThroughSetters() {
        try (SessionFactory sessionFactory = SessionFactoryTestSupport
                .h2Configuration(Person.class, NameTag.class)
                .buildSessionFactory()) {
            Person.persistAliceAndBob(sessionFactory);
            List<NameTag> tags = sessionFactory.fromTransaction(session -> session.createSelectionQuery(
                    "select new NameTag(upper(p.name) as label) from Person p order by p.name",
                    NameTag.class).getResultList());

            assertThat(tags).extracting(NameTag::getLabel).containsExactly("[ALICE]", "[BOB]");
        }
    }

    @Imported(rename = "NameTag")
    public static class NameTag {

        private String label;

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = "[" + label + "]";
        }
    }
}
