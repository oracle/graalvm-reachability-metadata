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
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.hibernate.event.spi.PreLoadEvent;
import org.hibernate.event.spi.PreLoadEventListener;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MetadataImplTest {

    @Test
    public void instantiatesAConfiguredEventListener() {
        Configuration configuration = new Configuration()
                .setProperty(AvailableSettings.URL, "jdbc:h2:mem:metadata-listener")
                .setProperty(AvailableSettings.DRIVER, "org.h2.Driver")
                .setProperty(AvailableSettings.DIALECT, "org.hibernate.dialect.H2Dialect")
                .setProperty(
                        AvailableSettings.EVENT_LISTENER_PREFIX + ".pre-load",
                        RecordingPreLoadListener.class.getName()
                )
                .setProperty(AvailableSettings.JAKARTA_VALIDATION_MODE, "none");

        try (SessionFactory factory = configuration.buildSessionFactory()) {
            EventListenerRegistry registry = ((SessionFactoryImplementor) factory)
                    .getServiceRegistry()
                    .getService(EventListenerRegistry.class);

            assertThat(registry.getEventListenerGroup(EventType.PRE_LOAD).listeners())
                    .anyMatch(RecordingPreLoadListener.class::isInstance);
        }
    }

    public static class RecordingPreLoadListener implements PreLoadEventListener {
        @Override
        public void onPreLoad(PreLoadEvent event) {
        }
    }
}
