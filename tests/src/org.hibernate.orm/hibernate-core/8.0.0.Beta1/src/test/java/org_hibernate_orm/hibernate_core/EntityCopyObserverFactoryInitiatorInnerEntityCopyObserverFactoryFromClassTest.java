/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_orm.hibernate_core;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.Configuration;
import org.hibernate.event.spi.EntityCopyObserver;
import org.hibernate.event.spi.EventSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class EntityCopyObserverFactoryInitiatorInnerEntityCopyObserverFactoryFromClassTest {

    private static final List<String> EVENTS = new CopyOnWriteArrayList<>();

    @Test
    void mergeOfDuplicateCopiesIsReportedToTheConfiguredObserver() {
        EVENTS.clear();
        Configuration configuration = SessionFactoryTestSupport.h2Configuration(Album.class, Track.class, Artist.class);
        configuration.setProperty(AvailableSettings.MERGE_ENTITY_COPY_OBSERVER, RecordingEntityCopyObserver.class.getName());

        try (SessionFactory sessionFactory = configuration.buildSessionFactory()) {
            sessionFactory.inTransaction(session -> session.persist(new Artist(1L, "Miles")));
            Album album = new Album(1L, "Kind of Blue");
            album.addTrack(new Track(1L, "So What", new Artist(1L, "Miles")));
            album.addTrack(new Track(2L, "Blue in Green", new Artist(1L, "Miles")));
            sessionFactory.inTransaction(session -> session.merge(album));
            Long tracks = sessionFactory.fromTransaction(session -> session.createSelectionQuery(
                    "select count(t) from Track t where t.artist.name = 'Miles'", Long.class).getSingleResult());

            assertThat(tracks).isEqualTo(2L);
        }
        assertThat(EVENTS).contains("copy:Miles", "merge-complete");
    }

    public static class RecordingEntityCopyObserver implements EntityCopyObserver {

        @Override
        public void entityCopyDetected(Object managedEntity, Object mergeEntity1, Object mergeEntity2, EventSource session) {
            if (managedEntity instanceof Artist artist) {
                EVENTS.add("copy:" + artist.getName());
            }
        }

        @Override
        public void topLevelMergeComplete(EventSource session) {
            EVENTS.add("merge-complete");
        }

        @Override
        public void clear() {
        }
    }

    @Entity(name = "Artist")
    public static class Artist {

        @Id
        private Long id;

        private String name;

        public Artist() {
        }

        public Artist(Long id, String name) {
            this.id = id;
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    @Entity(name = "Album")
    public static class Album {

        @Id
        private Long id;

        private String title;

        @OneToMany(mappedBy = "album", cascade = CascadeType.ALL)
        private List<Track> tracks = new ArrayList<>();

        public Album() {
        }

        public Album(Long id, String title) {
            this.id = id;
            this.title = title;
        }

        void addTrack(Track track) {
            track.album = this;
            tracks.add(track);
        }
    }

    @Entity(name = "Track")
    public static class Track {

        @Id
        private Long id;

        private String title;

        @ManyToOne(fetch = FetchType.EAGER)
        private Album album;

        @ManyToOne(fetch = FetchType.EAGER, cascade = CascadeType.MERGE)
        private Artist artist;

        public Track() {
        }

        public Track(Long id, String title, Artist artist) {
            this.id = id;
            this.title = title;
            this.artist = artist;
        }
    }
}
