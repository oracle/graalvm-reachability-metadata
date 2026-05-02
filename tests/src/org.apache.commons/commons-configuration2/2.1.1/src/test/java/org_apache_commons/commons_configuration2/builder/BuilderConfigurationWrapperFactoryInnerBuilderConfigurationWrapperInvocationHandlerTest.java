/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_configuration2.builder;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.configuration2.BaseConfiguration;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.builder.BuilderConfigurationWrapperFactory;
import org.apache.commons.configuration2.builder.BuilderConfigurationWrapperFactory.EventSourceSupport;
import org.apache.commons.configuration2.builder.ConfigurationBuilder;
import org.apache.commons.configuration2.builder.ConfigurationBuilderEvent;
import org.apache.commons.configuration2.event.Event;
import org.apache.commons.configuration2.event.EventListener;
import org.apache.commons.configuration2.event.EventListenerList;
import org.apache.commons.configuration2.event.EventSource;
import org.apache.commons.configuration2.event.EventType;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.junit.jupiter.api.Test;

public class BuilderConfigurationWrapperFactoryInnerBuilderConfigurationWrapperInvocationHandlerTest {
    @Test
    void delegatesConfigurationMethodsToCurrentBuilderConfiguration() {
        final TrackingConfigurationBuilder builder = new TrackingConfigurationBuilder();
        builder.getMutableConfiguration().addProperty("service.name", "commons-configuration");

        final Configuration wrapper = BuilderConfigurationWrapperFactory.createBuilderConfigurationWrapper(
                Configuration.class, builder, EventSourceSupport.NONE);

        assertThat(wrapper).isNotInstanceOf(EventSource.class);
        assertThat(wrapper.getString("service.name")).isEqualTo("commons-configuration");
        assertThat(builder.getConfigurationRequests()).isEqualTo(1);
    }

    @Test
    void delegatesEventSourceMethodsToBuilderWhenEnabled() {
        final TrackingConfigurationBuilder builder = new TrackingConfigurationBuilder();
        builder.getMutableConfiguration().addProperty("feature.enabled", true);
        final Configuration wrapper = BuilderConfigurationWrapperFactory.createBuilderConfigurationWrapper(
                Configuration.class, builder, EventSourceSupport.BUILDER);
        final EventSource eventSource = (EventSource) wrapper;
        final AtomicInteger requestEvents = new AtomicInteger();
        final EventListener<ConfigurationBuilderEvent> listener = event -> requestEvents.incrementAndGet();

        eventSource.addEventListener(ConfigurationBuilderEvent.CONFIGURATION_REQUEST, listener);

        assertThat(wrapper.getBoolean("feature.enabled")).isTrue();
        assertThat(requestEvents.get()).isEqualTo(1);
        assertThat(eventSource.removeEventListener(ConfigurationBuilderEvent.CONFIGURATION_REQUEST, listener)).isTrue();

        assertThat(wrapper.containsKey("feature.enabled")).isTrue();
        assertThat(requestEvents.get()).isEqualTo(1);
    }

    private static final class TrackingConfigurationBuilder implements ConfigurationBuilder<Configuration> {
        private final BaseConfiguration configuration = new BaseConfiguration();
        private final EventListenerList eventListeners = new EventListenerList();
        private int configurationRequests;

        BaseConfiguration getMutableConfiguration() {
            return configuration;
        }

        int getConfigurationRequests() {
            return configurationRequests;
        }

        @Override
        public Configuration getConfiguration() throws ConfigurationException {
            configurationRequests++;
            eventListeners.fire(new ConfigurationBuilderEvent(this,
                    ConfigurationBuilderEvent.CONFIGURATION_REQUEST));
            return configuration;
        }

        @Override
        public <T extends Event> void addEventListener(EventType<T> eventType,
                EventListener<? super T> listener) {
            eventListeners.addEventListener(eventType, listener);
        }

        @Override
        public <T extends Event> boolean removeEventListener(EventType<T> eventType,
                EventListener<? super T> listener) {
            return eventListeners.removeEventListener(eventType, listener);
        }
    }
}
