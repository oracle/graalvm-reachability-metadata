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

public class EmbeddableInstantiatorPojoStandardTest {

    @Test
    void embeddablePojoIsRecreatedThroughItsDefaultConstructor() {
        try (SessionFactory sessionFactory = SessionFactoryTestSupport
                .h2Configuration(Warehouse.class, Address.class)
                .buildSessionFactory()) {
            sessionFactory.inTransaction(session -> session.persist(new Warehouse(1L, new Address("Main St", "Springfield"))));
            Address address = sessionFactory.fromTransaction(session -> session.find(Warehouse.class, 1L).getAddress());

            assertThat(address.getStreet()).isEqualTo("Main St");
            assertThat(address.getCity()).isEqualTo("Springfield");
        }
    }

    @Embeddable
    public static class Address {

        private String street;

        private String city;

        public Address() {
        }

        public Address(String street, String city) {
            this.street = street;
            this.city = city;
        }

        public String getStreet() {
            return street;
        }

        public String getCity() {
            return city;
        }
    }

    @Entity(name = "Warehouse")
    public static class Warehouse {

        @Id
        private Long id;

        @Embedded
        private Address address;

        public Warehouse() {
        }

        public Warehouse(Long id, Address address) {
            this.id = id;
            this.address = address;
        }

        public Address getAddress() {
            return address;
        }
    }
}
