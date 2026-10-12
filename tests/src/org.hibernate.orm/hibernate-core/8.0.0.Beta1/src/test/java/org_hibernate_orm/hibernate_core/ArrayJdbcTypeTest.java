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

public class ArrayJdbcTypeTest {

    @Test
    void untypedNativeQueryResolvesTheJavaTypeOfAnArrayColumn() {
        try (SessionFactory sessionFactory = SessionFactoryTestSupport
                .h2Configuration(Sample.class)
                .buildSessionFactory()) {
            sessionFactory.inTransaction(session -> session.persist(new Sample(1L, new Integer[] {5, 8, 13})));
            Object readings = sessionFactory.fromTransaction(session -> session.createNativeQuery(
                    "select readings from Sample where id = 1").getSingleResult());

            assertThat(readings).isInstanceOf(Object[].class);
            assertThat((Object[]) readings).containsExactly(5, 8, 13);
        }
    }

    @Entity(name = "Sample")
    public static class Sample {

        @Id
        private Long id;

        private Integer[] readings;

        public Sample() {
        }

        public Sample(Long id, Integer[] readings) {
            this.id = id;
            this.readings = readings;
        }
    }
}
