/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_orm.hibernate_core;

import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Id;
import jakarta.persistence.Persistence;
import jakarta.persistence.PersistenceUtil;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PersistenceUtilHelperTest {

    @Test
    void persistenceUtilReportsAttributeLoadState() {
        try (SessionFactory sessionFactory = SessionFactoryTestSupport
                .h2Configuration(Musician.class)
                .buildSessionFactory()) {
            sessionFactory.inTransaction(session -> session.persist(new Musician(1L, "Nina", Set.of("piano", "voice"))));
            EntityManagerFactory entityManagerFactory = sessionFactory;
            PersistenceUtil persistenceUtil = Persistence.getPersistenceUtil();

            try (EntityManager entityManager = entityManagerFactory.createEntityManager()) {
                Musician musician = entityManager.find(Musician.class, 1L);

                assertThat(persistenceUtil.isLoaded(musician, "name")).isTrue();
                assertThat(persistenceUtil.isLoaded(musician, "instruments")).isFalse();
                assertThat(musician.getInstruments()).containsExactlyInAnyOrder("piano", "voice");
                assertThat(persistenceUtil.isLoaded(musician, "instruments")).isTrue();
                assertThat(persistenceUtil.isLoaded(musician)).isTrue();
            }
        }
    }

    @Test
    void persistenceUtilReadsPropertyAccessAttributesThroughGetters() {
        try (SessionFactory sessionFactory = SessionFactoryTestSupport
                .h2Configuration(Band.class)
                .buildSessionFactory()) {
            sessionFactory.inTransaction(session -> {
                Band band = new Band();
                band.setId(1L);
                band.setDisplayName("The Quartet");
                session.persist(band);
            });
            EntityManagerFactory entityManagerFactory = sessionFactory;

            try (EntityManager entityManager = entityManagerFactory.createEntityManager()) {
                Band band = entityManager.find(Band.class, 1L);

                assertThat(Persistence.getPersistenceUtil().isLoaded(band, "displayName")).isTrue();
                assertThat(band.getDisplayName()).isEqualTo("The Quartet");
            }
        }
    }

    @Entity(name = "Band")
    @Access(AccessType.PROPERTY)
    public static class Band {

        private Long id;

        private String label;

        @Id
        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getDisplayName() {
            return label;
        }

        public void setDisplayName(String displayName) {
            label = displayName;
        }
    }

    @Entity(name = "Musician")
    public static class Musician {

        @Id
        private Long id;

        private String name;

        @ElementCollection
        private Set<String> instruments = new HashSet<>();

        public Musician() {
        }

        public Musician(Long id, String name, Set<String> instruments) {
            this.id = id;
            this.name = name;
            this.instruments = new HashSet<>(instruments);
        }

        public String getName() {
            return name;
        }

        public Set<String> getInstruments() {
            return instruments;
        }
    }
}
