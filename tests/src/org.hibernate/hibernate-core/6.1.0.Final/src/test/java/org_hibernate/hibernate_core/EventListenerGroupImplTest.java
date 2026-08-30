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

public class EventListenerGroupImplTest {

    @Test
    public void appendsAndPrependsListenerInstances() {
        try (SessionFactory factory = createSessionFactory()) {
            EventListenerRegistry registry = ((SessionFactoryImplementor) factory)
                    .getServiceRegistry()
                    .getService(EventListenerRegistry.class);
            EventListenerGroup<PreLoadEventListener> group =
                    registry.getEventListenerGroup(EventType.PRE_LOAD);
            FirstListener first = new FirstListener();
            SecondListener second = new SecondListener();

            group.prependListener(second);
            group.appendListener(first);

            List<PreLoadEventListener> listeners = new ArrayList<>();
            for (PreLoadEventListener listener : group.listeners()) {
                listeners.add(listener);
            }
            assertThat(listeners.get(0)).isSameAs(second);
            assertThat(listeners.get(listeners.size() - 1)).isSameAs(first);
        }
    }

    private static SessionFactory createSessionFactory() {
        return new Configuration()
                .setProperty(AvailableSettings.URL, "jdbc:h2:mem:event-group")
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
