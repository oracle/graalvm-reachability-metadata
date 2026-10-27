/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_orm.hibernate_core;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BasicCollectionJavaTypeTest {

    @Test
    void basicListAttributeIsStoredAsASqlArray() {
        try (SessionFactory sessionFactory = SessionFactoryTestSupport
                .h2Configuration(Playlist.class)
                .buildSessionFactory()) {
            sessionFactory.inTransaction(session -> session.persist(
                    new Playlist(1L, new ArrayList<>(List.of("intro", "verse", "chorus")))));
            sessionFactory.inTransaction(session -> session.find(Playlist.class, 1L).getTracks().add("outro"));
            List<String> tracks = sessionFactory.fromTransaction(session -> new ArrayList<>(
                    session.find(Playlist.class, 1L).getTracks()));

            assertThat(tracks).containsExactly("intro", "verse", "chorus", "outro");
        }
    }

    @Entity(name = "Playlist")
    public static class Playlist {

        @Id
        private Long id;

        private List<String> tracks;

        public Playlist() {
        }

        public Playlist(Long id, List<String> tracks) {
            this.id = id;
            this.tracks = tracks;
        }

        public List<String> getTracks() {
            return tracks;
        }
    }
}
