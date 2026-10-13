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

public class FloatPrimitiveArrayJavaTypeTest {

    @Test
    void primitiveArrayAttributeRoundTripsThroughASqlArray() {
        try (SessionFactory sessionFactory = SessionFactoryTestSupport
                .h2Configuration(Waveform.class)
                .buildSessionFactory()) {
            sessionFactory.inTransaction(session -> session.persist(new Waveform(1L, new float[] {0.25f, 0.5f})));
            float[] loaded = sessionFactory.fromTransaction(session -> session.find(Waveform.class, 1L).getValues());
            sessionFactory.inTransaction(session -> session.find(Waveform.class, 1L).getValues()[0] = 0.75f);
            float[] updated = sessionFactory.fromTransaction(session -> session.find(Waveform.class, 1L).getValues());

            assertThat(loaded).containsExactly(0.25f, 0.5f);
            assertThat(updated[0]).isEqualTo(0.75f);
        }
    }

    @Entity(name = "Waveform")
    public static class Waveform {

        @Id
        private Long id;

        private float[] samples;

        public Waveform() {
        }

        public Waveform(Long id, float[] values) {
            this.id = id;
            this.samples = values;
        }

        public float[] getValues() {
            return samples;
        }
    }
}
