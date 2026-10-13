/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_orm.hibernate_core;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OrderColumn;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ArrayTypeTest {

    @Test
    void mergingADetachedEntityCopiesItsArrayCollection() {
        try (SessionFactory sessionFactory = SessionFactoryTestSupport
                .h2Configuration(Route.class)
                .buildSessionFactory()) {
            sessionFactory.inTransaction(session -> session.persist(new Route(1L, new String[] {"Paris", "Lyon"})));
            Route detached = sessionFactory.fromTransaction(session -> session.find(Route.class, 1L));
            detached.setStops(new String[] {"Paris", "Dijon", "Lyon"});
            sessionFactory.inTransaction(session -> session.merge(detached));
            String[] stops = sessionFactory.fromTransaction(session -> session.find(Route.class, 1L).getStops());

            assertThat(stops).containsExactly("Paris", "Dijon", "Lyon");
        }
    }

    @Entity(name = "Route")
    public static class Route {

        @Id
        private Long id;

        @ElementCollection(fetch = FetchType.EAGER)
        @CollectionTable(name = "Route_stops")
        @OrderColumn
        private String[] stops;

        public Route() {
        }

        public Route(Long id, String[] stops) {
            this.id = id;
            this.stops = stops;
        }

        public String[] getStops() {
            return stops;
        }

        public void setStops(String[] stops) {
            this.stops = stops;
        }
    }
}
