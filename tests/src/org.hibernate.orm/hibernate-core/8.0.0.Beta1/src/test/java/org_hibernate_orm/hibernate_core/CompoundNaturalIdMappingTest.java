/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_orm.hibernate_core;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.hibernate.KeyType;
import org.hibernate.SessionFactory;
import org.hibernate.annotations.NaturalId;
import org.hibernate.annotations.NaturalIdClass;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CompoundNaturalIdMappingTest {

    @Test
    void recordNaturalIdClassLoadsTheEntity() {
        try (SessionFactory sessionFactory = SessionFactoryTestSupport
                .h2Configuration(Edition.class)
                .buildSessionFactory()) {
            sessionFactory.inTransaction(session -> session.persist(new Edition(1L, "978-0", 2, "Second edition")));
            String title = sessionFactory.fromTransaction(session -> session.find(
                    Edition.class, new EditionKey("978-0", 2), KeyType.NATURAL).getTitle());

            assertThat(title).isEqualTo("Second edition");
        }
    }

    @Test
    void beanNaturalIdClassWithGettersLoadsTheEntity() {
        try (SessionFactory sessionFactory = SessionFactoryTestSupport
                .h2Configuration(Flight.class)
                .buildSessionFactory()) {
            sessionFactory.inTransaction(session -> session.persist(new Flight(1L, "LX", 318, "Zurich")));
            String destination = sessionFactory.fromTransaction(session -> session.find(
                    Flight.class, new FlightKey("LX", 318), KeyType.NATURAL).getDestination());

            assertThat(destination).isEqualTo("Zurich");
        }
    }

    @Test
    void fieldOnlyNaturalIdClassLoadsTheEntity() {
        try (SessionFactory sessionFactory = SessionFactoryTestSupport
                .h2Configuration(Vat.class)
                .buildSessionFactory()) {
            sessionFactory.inTransaction(session -> session.persist(new Vat(1L, "CH", "reduced", 26)));
            Integer rate = sessionFactory.fromTransaction(session -> session.find(
                    Vat.class, new VatKey("CH", "reduced"), KeyType.NATURAL).getPerMille());

            assertThat(rate).isEqualTo(26);
        }
    }

    public record EditionKey(String isbn, Integer edition) {
    }

    @Entity(name = "Edition")
    @NaturalIdClass(EditionKey.class)
    public static class Edition {

        @Id
        private Long id;

        @NaturalId
        private String isbn;

        @NaturalId
        private Integer edition;

        private String title;

        public Edition() {
        }

        public Edition(Long id, String isbn, Integer edition, String title) {
            this.id = id;
            this.isbn = isbn;
            this.edition = edition;
            this.title = title;
        }

        public String getTitle() {
            return title;
        }
    }

    public static class FlightKey {

        private final String carrier;

        private final Integer flightNumber;

        public FlightKey(String carrier, Integer flightNumber) {
            this.carrier = carrier;
            this.flightNumber = flightNumber;
        }

        public String getCarrier() {
            return carrier;
        }

        public Integer getFlightNumber() {
            return flightNumber;
        }
    }

    @Entity(name = "Flight")
    @NaturalIdClass(FlightKey.class)
    public static class Flight {

        @Id
        private Long id;

        @NaturalId
        private String carrier;

        @NaturalId
        private Integer flightNumber;

        private String destination;

        public Flight() {
        }

        public Flight(Long id, String carrier, Integer flightNumber, String destination) {
            this.id = id;
            this.carrier = carrier;
            this.flightNumber = flightNumber;
            this.destination = destination;
        }

        public String getDestination() {
            return destination;
        }
    }

    public static class VatKey {

        public final String country;

        public final String category;

        public VatKey(String country, String category) {
            this.country = country;
            this.category = category;
        }
    }

    @Entity(name = "Vat")
    @NaturalIdClass(VatKey.class)
    public static class Vat {

        @Id
        private Long id;

        @NaturalId
        private String country;

        @NaturalId
        private String category;

        private Integer perMille;

        public Vat() {
        }

        public Vat(Long id, String country, String category, Integer perMille) {
            this.id = id;
            this.country = country;
            this.category = category;
            this.perMille = perMille;
        }

        public Integer getPerMille() {
            return perMille;
        }
    }
}
