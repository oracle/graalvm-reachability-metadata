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

public class DoublePrimitiveArrayJavaTypeTest {

    @Test
    void primitiveArrayAttributeRoundTripsThroughASqlArray() {
        try (SessionFactory sessionFactory = SessionFactoryTestSupport
                .h2Configuration(Spectrum.class)
                .buildSessionFactory()) {
            sessionFactory.inTransaction(session -> session.persist(new Spectrum(1L, new double[] {0.5, 1.5, 2.5})));
            double[] loaded = sessionFactory.fromTransaction(session -> session.find(Spectrum.class, 1L).getValues());
            sessionFactory.inTransaction(session -> session.find(Spectrum.class, 1L).getValues()[0] = 9.5);
            double[] updated = sessionFactory.fromTransaction(session -> session.find(Spectrum.class, 1L).getValues());

            assertThat(loaded).containsExactly(0.5, 1.5, 2.5);
            assertThat(updated[0]).isEqualTo(9.5);
        }
    }

    @Entity(name = "Spectrum")
    public static class Spectrum {

        @Id
        private Long id;

        private double[] samples;

        public Spectrum() {
        }

        public Spectrum(Long id, double[] values) {
            this.id = id;
            this.samples = values;
        }

        public double[] getValues() {
            return samples;
        }
    }
}
