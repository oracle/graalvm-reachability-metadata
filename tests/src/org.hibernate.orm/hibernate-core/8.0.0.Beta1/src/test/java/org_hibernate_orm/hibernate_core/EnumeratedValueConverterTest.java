/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_orm.hibernate_core;

import java.util.List;

import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.EnumeratedValue;
import jakarta.persistence.Id;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class EnumeratedValueConverterTest {

    @Test
    void enumIsStoredAndReadThroughItsEnumeratedValueField() {
        try (SessionFactory sessionFactory = SessionFactoryTestSupport
                .h2Configuration(Order.class)
                .buildSessionFactory()) {
            sessionFactory.inTransaction(session -> {
                session.persist(new Order(1L, Status.SHIPPED));
                session.persist(new Order(2L, Status.PENDING));
            });
            List<Integer> codes = sessionFactory.fromTransaction(session -> session.createNativeQuery(
                    "select status from Orders order by id", Integer.class).getResultList());
            Status status = sessionFactory.fromTransaction(session -> session.find(Order.class, 1L).getStatus());

            assertThat(codes).containsExactly(200, 100);
            assertThat(status).isEqualTo(Status.SHIPPED);
        }
    }

    public enum Status {
        PENDING(100),
        SHIPPED(200);

        @EnumeratedValue
        private final int code;

        Status(int code) {
            this.code = code;
        }
    }

    @Entity(name = "Orders")
    public static class Order {

        @Id
        private Long id;

        @Enumerated(EnumType.ORDINAL)
        private Status status;

        public Order() {
        }

        public Order(Long id, Status status) {
            this.id = id;
            this.status = status;
        }

        public Status getStatus() {
            return status;
        }
    }
}
