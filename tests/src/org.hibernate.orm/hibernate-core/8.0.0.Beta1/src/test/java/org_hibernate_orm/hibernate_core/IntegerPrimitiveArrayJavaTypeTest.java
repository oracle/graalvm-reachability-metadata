/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_orm.hibernate_core;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class IntegerPrimitiveArrayJavaTypeTest {

    @Test
    void primitiveArrayAttributeRoundTripsThroughASqlArray() {
        try (SessionFactory sessionFactory = SessionFactoryTestSupport
                .h2Configuration(Scoreboard.class)
                .buildSessionFactory()) {
            sessionFactory.inTransaction(session -> session.persist(new Scoreboard(1L, new int[] {3, 1, 4})));
            int[] loaded = sessionFactory.fromTransaction(session -> session.find(Scoreboard.class, 1L).getValues());
            sessionFactory.inTransaction(session -> session.find(Scoreboard.class, 1L).getValues()[0] = 9);
            int[] updated = sessionFactory.fromTransaction(session -> session.find(Scoreboard.class, 1L).getValues());

            assertThat(loaded).containsExactly(3, 1, 4);
            assertThat(updated[0]).isEqualTo(9);
        }
    }

    @Entity(name = "Scoreboard")
    public static class Scoreboard {

        @Id
        private Long id;

        private int[] samples;

        public Scoreboard() {
        }

        public Scoreboard(Long id, int[] values) {
            this.id = id;
            this.samples = values;
        }

        public int[] getValues() {
            return samples;
        }
    }
}
