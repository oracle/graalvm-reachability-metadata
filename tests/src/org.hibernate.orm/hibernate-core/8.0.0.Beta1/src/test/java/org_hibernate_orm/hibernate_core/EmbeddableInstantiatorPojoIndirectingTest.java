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
import org.hibernate.annotations.Instantiator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class EmbeddableInstantiatorPojoIndirectingTest {

    @Test
    void annotatedConstructorReceivesValuesInItsDeclaredOrder() {
        try (SessionFactory sessionFactory = SessionFactoryTestSupport
                .h2Configuration(Shipment.class, Parcel.class)
                .buildSessionFactory()) {
            sessionFactory.inTransaction(session -> session.persist(new Shipment(1L, new Parcel(4, 12))));
            Parcel parcel = sessionFactory.fromTransaction(session -> session.find(Shipment.class, 1L).getParcel());

            assertThat(parcel.weight()).isEqualTo(12);
            assertThat(parcel.pieces()).isEqualTo(4);
        }
    }

    @Embeddable
    public static class Parcel {

        private final Integer weight;

        private final Integer pieces;

        @Instantiator({"weight", "pieces"})
        public Parcel(Integer weight, Integer pieces) {
            this.weight = weight;
            this.pieces = pieces;
        }

        public Parcel(int pieces, int weight) {
            this(Integer.valueOf(weight), Integer.valueOf(pieces));
        }

        public Integer weight() {
            return weight;
        }

        public Integer pieces() {
            return pieces;
        }
    }

    @Entity(name = "Shipment")
    public static class Shipment {

        @Id
        private Long id;

        @Embedded
        private Parcel parcel;

        public Shipment() {
        }

        public Shipment(Long id, Parcel parcel) {
            this.id = id;
            this.parcel = parcel;
        }

        public Parcel getParcel() {
            return parcel;
        }
    }
}
