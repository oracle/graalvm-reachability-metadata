/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_orm.hibernate_core;

import java.io.Serializable;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SerializationHelperTest {

    @Test
    void serializableAttributeIsStoredAsBinaryAndRestored() {
        try (SessionFactory sessionFactory = SessionFactoryTestSupport
                .h2Configuration(Snapshot.class)
                .buildSessionFactory()) {
            sessionFactory.inTransaction(session -> session.persist(
                    new Snapshot(1L, new Viewport(1920, 1080, "landscape"))));
            Viewport viewport = sessionFactory.fromTransaction(session -> session.find(Snapshot.class, 1L).getViewport());

            assertThat(viewport.width()).isEqualTo(1920);
            assertThat(viewport.height()).isEqualTo(1080);
            assertThat(viewport.orientation()).isEqualTo("landscape");
        }
    }

    public static final class Viewport implements Serializable {

        private final int width;

        private final int height;

        private final String orientation;

        public Viewport(int width, int height, String orientation) {
            this.width = width;
            this.height = height;
            this.orientation = orientation;
        }

        public int width() {
            return width;
        }

        public int height() {
            return height;
        }

        public String orientation() {
            return orientation;
        }
    }

    @Entity(name = "Snapshot")
    public static class Snapshot {

        @Id
        private Long id;

        private Viewport viewport;

        public Snapshot() {
        }

        public Snapshot(Long id, Viewport viewport) {
            this.id = id;
            this.viewport = viewport;
        }

        public Viewport getViewport() {
            return viewport;
        }
    }
}
