/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_orm.hibernate_core;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.hibernate.SessionFactory;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.registry.BootstrapServiceRegistry;
import org.hibernate.boot.registry.BootstrapServiceRegistryBuilder;
import org.hibernate.boot.spi.BootstrapContext;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.spi.EventType;
import org.hibernate.event.spi.PostInsertEvent;
import org.hibernate.event.spi.PostInsertEventListener;
import org.hibernate.event.spi.PreDeleteEvent;
import org.hibernate.event.spi.PreDeleteEventListener;
import org.hibernate.integrator.spi.Integrator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class EventListenerRegistryImplTest {

    private static final List<String> EVENTS = new CopyOnWriteArrayList<>();

    @Test
    void listenersRegisteredByClassReceiveEntityEvents() {
        EVENTS.clear();
        BootstrapServiceRegistry bootstrapRegistry = new BootstrapServiceRegistryBuilder()
                .applyIntegrator(new ListenerClassIntegrator())
                .build();

        try (SessionFactory sessionFactory = SessionFactoryTestSupport
                .h2Configuration(bootstrapRegistry, Note.class)
                .buildSessionFactory()) {
            sessionFactory.inTransaction(session -> session.persist(new Note(1L, "remember the milk")));
            sessionFactory.inTransaction(session -> session.remove(session.find(Note.class, 1L)));
        }

        assertThat(EVENTS).containsExactly("inserted:remember the milk", "deleting:remember the milk");
    }

    public static class ListenerClassIntegrator implements Integrator {

        @Override
        public void integrate(
                Metadata metadata,
                BootstrapContext bootstrapContext,
                SessionFactoryImplementor sessionFactory) {
            sessionFactory.getEventListenerRegistry()
                    .appendListeners(EventType.POST_INSERT, InsertRecordingListener.class);
            sessionFactory.getEventListenerRegistry()
                    .setListeners(EventType.PRE_DELETE, DeleteRecordingListener.class);
        }
    }

    public static class InsertRecordingListener implements PostInsertEventListener {

        @Override
        public void onPostInsert(PostInsertEvent event) {
            if (event.getEntity() instanceof Note note) {
                EVENTS.add("inserted:" + note.getText());
            }
        }
    }

    public static class DeleteRecordingListener implements PreDeleteEventListener {

        @Override
        public boolean onPreDelete(PreDeleteEvent event) {
            if (event.getEntity() instanceof Note note) {
                EVENTS.add("deleting:" + note.getText());
            }
            return false;
        }
    }

    @Entity(name = "Note")
    public static class Note {

        @Id
        private Long id;

        private String text;

        public Note() {
        }

        public Note(Long id, String text) {
            this.id = id;
            this.text = text;
        }

        public String getText() {
            return text;
        }
    }
}
