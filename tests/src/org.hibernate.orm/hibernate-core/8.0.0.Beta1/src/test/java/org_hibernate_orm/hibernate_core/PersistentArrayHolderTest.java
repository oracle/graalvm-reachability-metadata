/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_orm.hibernate_core;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OrderColumn;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PersistentArrayHolderTest {

    @Test
    void arrayElementCollectionIsLoadedAndUpdated() {
        try (SessionFactory sessionFactory = SessionFactoryTestSupport
                .h2Configuration(Recipe.class)
                .buildSessionFactory()) {
            sessionFactory.inTransaction(session -> session.persist(
                    new Recipe(1L, new String[] {"flour", "water", "salt"})));
            sessionFactory.inTransaction(session -> {
                Recipe recipe = session.find(Recipe.class, 1L);
                recipe.setSteps(new String[] {"flour", "water", "yeast", "salt"});
            });
            String[] steps = sessionFactory.fromTransaction(session -> session.find(Recipe.class, 1L).getSteps());

            assertThat(steps).containsExactly("flour", "water", "yeast", "salt");
        }
    }

    @Entity(name = "Recipe")
    public static class Recipe {

        @Id
        private Long id;

        @ElementCollection(fetch = FetchType.EAGER)
        @CollectionTable(name = "Recipe_steps")
        @OrderColumn
        private String[] steps;

        public Recipe() {
        }

        public Recipe(Long id, String[] steps) {
            this.id = id;
            this.steps = steps;
        }

        public String[] getSteps() {
            return steps;
        }

        public void setSteps(String[] steps) {
            this.steps = steps;
        }
    }
}
