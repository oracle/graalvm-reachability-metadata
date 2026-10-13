/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_orm.hibernate_core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class StatefulPersistenceContextTest {

    @Test
    void serializedSessionKeepsItsManagedEntitiesAndCollections() throws Exception {
        try (SessionFactory sessionFactory = SessionFactoryTestSupport
                .h2Configuration(Library.class, Shelf.class)
                .buildSessionFactory()) {
            sessionFactory.inTransaction(session -> {
                Library library = new Library(1L, "Central");
                session.persist(library);
                session.persist(new Shelf(10L, "Fiction", library));
                session.persist(new Shelf(11L, "History", library));
            });

            Session session = sessionFactory.openSession();
            session.beginTransaction();
            Library library = session.find(Library.class, 1L);
            assertThat(library.getShelves()).hasSize(2);
            session.setReadOnly(library, true);
            session.getTransaction().commit();

            Session restored = (Session) deserialize(serialize(session));
            session.close();
            try (restored) {
                Library restoredLibrary = restored.find(Library.class, 1L);

                assertThat(restored.contains(restoredLibrary)).isTrue();
                assertThat(restored.isReadOnly(restoredLibrary)).isTrue();
                assertThat(Hibernate.isInitialized(restoredLibrary.getShelves())).isTrue();
                assertThat(restoredLibrary.getShelves()).extracting(Shelf::getGenre)
                        .containsExactlyInAnyOrder("Fiction", "History");
            }
        }
    }

    private static byte[] serialize(Object value) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }
        return bytes.toByteArray();
    }

    private static Object deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return input.readObject();
        }
    }

    @Entity(name = "Library")
    public static class Library implements Serializable {

        @Id
        private Long id;

        private String name;

        @OneToMany(mappedBy = "library", fetch = FetchType.EAGER)
        private List<Shelf> shelves = new ArrayList<>();

        public Library() {
        }

        public Library(Long id, String name) {
            this.id = id;
            this.name = name;
        }

        public List<Shelf> getShelves() {
            return shelves;
        }
    }

    @Entity(name = "Shelf")
    public static class Shelf implements Serializable {

        @Id
        private Long id;

        private String genre;

        @ManyToOne(fetch = FetchType.EAGER)
        private Library library;

        public Shelf() {
        }

        public Shelf(Long id, String genre, Library library) {
            this.id = id;
            this.genre = genre;
            this.library = library;
        }

        public String getGenre() {
            return genre;
        }
    }
}
