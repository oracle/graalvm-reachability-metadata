/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_orm.hibernate_core;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CollectionConverterTest {

    @Test
    void convertedListElementsAreStoredAsARelationalArray() {
        try (SessionFactory sessionFactory = SessionFactoryTestSupport
                .h2Configuration(Itinerary.class)
                .buildSessionFactory()) {
            sessionFactory.inTransaction(session -> session.persist(
                    new Itinerary(1L, new ArrayList<>(List.of(new Airport("ZRH"), new Airport("SFO"))))));
            List<Airport> airports = sessionFactory.fromTransaction(session -> new ArrayList<>(
                    session.find(Itinerary.class, 1L).getAirports()));
            Object stored = sessionFactory.fromTransaction(session -> session.createNativeQuery(
                    "select airports from Itinerary").getSingleResult());

            assertThat(airports).containsExactly(new Airport("ZRH"), new Airport("SFO"));
            assertThat((Object[]) stored).containsExactly("zrh", "sfo");
        }
    }

    public record Airport(String code) {
    }

    public static class AirportCodeConverter implements AttributeConverter<Airport, String> {

        @Override
        public String convertToDatabaseColumn(Airport airport) {
            return airport.code().toLowerCase();
        }

        @Override
        public Airport convertToEntityAttribute(String code) {
            return new Airport(code.toUpperCase());
        }
    }

    @Entity(name = "Itinerary")
    public static class Itinerary {

        @Id
        private Long id;

        @Convert(converter = AirportCodeConverter.class)
        private List<Airport> airports;

        public Itinerary() {
        }

        public Itinerary(Long id, List<Airport> airports) {
            this.id = id;
            this.airports = airports;
        }

        public List<Airport> getAirports() {
            return airports;
        }
    }
}
