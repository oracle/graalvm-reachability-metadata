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

public class BooleanPrimitiveArrayJavaTypeTest {

    @Test
    void primitiveArrayAttributeRoundTripsThroughASqlArray() {
        try (SessionFactory sessionFactory = SessionFactoryTestSupport
                .h2Configuration(Switchboard.class)
                .buildSessionFactory()) {
            sessionFactory.inTransaction(session -> session.persist(new Switchboard(1L, new boolean[] {true, false, true})));
            boolean[] loaded = sessionFactory.fromTransaction(session -> session.find(Switchboard.class, 1L).getValues());
            sessionFactory.inTransaction(session -> session.find(Switchboard.class, 1L).getValues()[0] = false);
            boolean[] updated = sessionFactory.fromTransaction(session -> session.find(Switchboard.class, 1L).getValues());

            assertThat(loaded).containsExactly(true, false, true);
            assertThat(updated[0]).isEqualTo(false);
        }
    }

    @Entity(name = "Switchboard")
    public static class Switchboard {

        @Id
        private Long id;

        private boolean[] samples;

        public Switchboard() {
        }

        public Switchboard(Long id, boolean[] values) {
            this.id = id;
            this.samples = values;
        }

        public boolean[] getValues() {
            return samples;
        }
    }
}
