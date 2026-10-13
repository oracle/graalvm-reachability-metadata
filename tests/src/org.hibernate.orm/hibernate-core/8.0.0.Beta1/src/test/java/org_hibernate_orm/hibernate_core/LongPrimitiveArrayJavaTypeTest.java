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

public class LongPrimitiveArrayJavaTypeTest {

    @Test
    void primitiveArrayAttributeRoundTripsThroughASqlArray() {
        try (SessionFactory sessionFactory = SessionFactoryTestSupport
                .h2Configuration(Ledger.class)
                .buildSessionFactory()) {
            sessionFactory.inTransaction(session -> session.persist(new Ledger(1L, new long[] {10L, 20L, 30L})));
            long[] loaded = sessionFactory.fromTransaction(session -> session.find(Ledger.class, 1L).getValues());
            sessionFactory.inTransaction(session -> session.find(Ledger.class, 1L).getValues()[0] = 99L);
            long[] updated = sessionFactory.fromTransaction(session -> session.find(Ledger.class, 1L).getValues());

            assertThat(loaded).containsExactly(10L, 20L, 30L);
            assertThat(updated[0]).isEqualTo(99L);
        }
    }

    @Entity(name = "Ledger")
    public static class Ledger {

        @Id
        private Long id;

        private long[] samples;

        public Ledger() {
        }

        public Ledger(Long id, long[] values) {
            this.id = id;
            this.samples = values;
        }

        public long[] getValues() {
            return samples;
        }
    }
}
