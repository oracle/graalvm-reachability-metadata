/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_orm.hibernate_core;

import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class EmbeddableInstantiatorRecordStandardTest {

    @Test
    void recordWithAlphabeticalComponentsIsCreatedThroughItsCanonicalConstructor() {
        try (SessionFactory sessionFactory = SessionFactoryTestSupport
                .h2Configuration(Thermostat.class, Range.class)
                .buildSessionFactory()) {
            sessionFactory.inTransaction(session -> session.persist(new Thermostat(1L, new Range(24, 18))));
            Range range = sessionFactory.fromTransaction(session -> session.find(Thermostat.class, 1L).getRange());

            assertThat(range).isEqualTo(new Range(24, 18));
        }
    }

    @Embeddable
    public record Range(Integer max, Integer min) {
    }

    @Entity(name = "Thermostat")
    public static class Thermostat {

        @Id
        private Long id;

        @Embedded
        private Range range;

        public Thermostat() {
        }

        public Thermostat(Long id, Range range) {
            this.id = id;
            this.range = range;
        }

        public Range getRange() {
            return range;
        }
    }
}
