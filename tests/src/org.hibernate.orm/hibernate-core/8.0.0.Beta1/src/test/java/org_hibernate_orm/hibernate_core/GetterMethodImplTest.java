/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_orm.hibernate_core;

import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class GetterMethodImplTest {

    @Test
    void propertyAccessReadsStateThroughGetters() {
        try (SessionFactory sessionFactory = SessionFactoryTestSupport
                .h2Configuration(Thermometer.class)
                .buildSessionFactory()) {
            Thermometer thermometer = new Thermometer();
            thermometer.setId(1L);
            thermometer.setCelsius(21.5);
            sessionFactory.inTransaction(session -> session.persist(thermometer));
            Double fahrenheit = sessionFactory.fromTransaction(session -> session.createSelectionQuery(
                    "select t.celsius * 9 / 5 + 32 from Thermometer t", Double.class).getSingleResult());

            assertThat(thermometer.getterCalls).isPositive();
            assertThat(fahrenheit).isEqualTo(70.7);
        }
    }

    @Entity(name = "Thermometer")
    @Access(AccessType.PROPERTY)
    public static class Thermometer {

        private Long id;

        private double celsius;

        private int getterCalls;

        @Id
        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public double getCelsius() {
            getterCalls++;
            return celsius;
        }

        public void setCelsius(double celsius) {
            this.celsius = celsius;
        }
    }
}
