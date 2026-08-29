/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.SessionFactory;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.Configuration;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerGroup;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.hibernate.event.spi.PreLoadEvent;
import org.hibernate.event.spi.PreLoadEventListener;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class EventListenerRegistryImplTest {

    @Test
    public void createsListenersFromConfiguredClasses() {
        try (SessionFactory factory = createSessionFactory()) {
            EventListenerRegistry registry = ((SessionFactoryImplementor) factory)
                    .getServiceRegistry()
                    .getService(EventListenerRegistry.class);

            registry.setListeners(
                    EventType.PRE_LOAD,
                    FirstListener.class,
                    SecondListener.class
            );
            EventListenerGroup<PreLoadEventListener> group =
                    registry.getEventListenerGroup(EventType.PRE_LOAD);

            List<Class<?>> listenerTypes = new ArrayList<>();
            for (PreLoadEventListener listener : group.listeners()) {
                listenerTypes.add(listener.getClass());
            }
            assertThat(listenerTypes).containsExactly(
                    FirstListener.class,
                    SecondListener.class
            );
        }
    }

    private static SessionFactory createSessionFactory() {
        return new Configuration()
                .setProperty(AvailableSettings.URL, "jdbc:h2:mem:event-registry")
                .setProperty(AvailableSettings.DRIVER, "org.h2.Driver")
                .setProperty(AvailableSettings.DIALECT, "org.hibernate.dialect.H2Dialect")
                .buildSessionFactory();
    }

    public static class FirstListener implements PreLoadEventListener {
        @Override
        public void onPreLoad(PreLoadEvent event) {
        }
    }

    public static class SecondListener implements PreLoadEventListener {
        @Override
        public void onPreLoad(PreLoadEvent event) {
        }
    }
}
