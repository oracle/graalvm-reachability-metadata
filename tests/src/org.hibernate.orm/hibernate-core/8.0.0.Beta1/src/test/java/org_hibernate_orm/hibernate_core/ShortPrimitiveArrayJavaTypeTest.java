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

public class ShortPrimitiveArrayJavaTypeTest {

    @Test
    void primitiveArrayAttributeRoundTripsThroughASqlArray() {
        try (SessionFactory sessionFactory = SessionFactoryTestSupport
                .h2Configuration(Histogram.class)
                .buildSessionFactory()) {
            sessionFactory.inTransaction(session -> session.persist(new Histogram(1L, new short[] {(short) 7, (short) 8})));
            short[] loaded = sessionFactory.fromTransaction(session -> session.find(Histogram.class, 1L).getValues());
            sessionFactory.inTransaction(session -> session.find(Histogram.class, 1L).getValues()[0] = (short) 9);
            short[] updated = sessionFactory.fromTransaction(session -> session.find(Histogram.class, 1L).getValues());

            assertThat(loaded).containsExactly((short) 7, (short) 8);
            assertThat(updated[0]).isEqualTo((short) 9);
        }
    }

    @Entity(name = "Histogram")
    public static class Histogram {

        @Id
        private Long id;

        private short[] samples;

        public Histogram() {
        }

        public Histogram(Long id, short[] values) {
            this.id = id;
            this.samples = values;
        }

        public short[] getValues() {
            return samples;
        }
    }
}
