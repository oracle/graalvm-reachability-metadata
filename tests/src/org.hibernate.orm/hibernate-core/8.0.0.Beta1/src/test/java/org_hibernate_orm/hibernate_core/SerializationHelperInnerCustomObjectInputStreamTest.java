/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_orm.hibernate_core;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SerializationHelperInnerCustomObjectInputStreamTest {

    @Test
    void serializedObjectGraphResolvesItsApplicationClasses() {
        try (SessionFactory sessionFactory = SessionFactoryTestSupport
                .h2Configuration(Board.class)
                .buildSessionFactory()) {
            Layout layout = new Layout(new Widget("clock", 1), new Widget("weather", 2));
            sessionFactory.inTransaction(session -> session.persist(new Board(1L, layout)));
            Layout loaded = sessionFactory.fromTransaction(session -> session.find(Board.class, 1L).getLayout());

            assertThat(loaded.primary()).isEqualTo(new Widget("clock", 1));
            assertThat(loaded.secondary()).isEqualTo(new Widget("weather", 2));
        }
    }

    public static final class Widget implements Serializable {

        private final String name;

        private final int column;

        public Widget(String name, int column) {
            this.name = name;
            this.column = column;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Widget widget && name.equals(widget.name) && column == widget.column;
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, column);
        }
    }

    public static final class Layout implements Serializable {

        private final Widget primary;

        private final Widget secondary;

        public Layout(Widget primary, Widget secondary) {
            this.primary = primary;
            this.secondary = secondary;
        }

        public Widget primary() {
            return primary;
        }

        public Widget secondary() {
            return secondary;
        }
    }

    @Entity(name = "Board")
    public static class Board {

        @Id
        private Long id;

        @Column(length = 2048)
        private Layout layout;

        public Board() {
        }

        public Board(Long id, Layout layout) {
            this.id = id;
            this.layout = layout;
        }

        public Layout getLayout() {
            return layout;
        }
    }
}
