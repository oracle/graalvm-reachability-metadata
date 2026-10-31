/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus.plexus_container_default;

import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusConstants;
import org.codehaus.plexus.lifecycle.AbstractLifecycleHandler;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultPlexusContainerTest {
    @Test
    void constructorInitializesCoreRealmAndContainerContext() throws Exception {
        DefaultPlexusContainer container = new DefaultPlexusContainer(
            "test-container",
            null
        );

        try {
            container.setReloadingEnabled(true);

            assertThat(container.getName()).isEqualTo("test-container");
            assertThat(container.getContainerRealm()).isNotNull();
            assertThat(container.getLoggerManager()).isNotNull();
            AbstractLifecycleHandler bootstrap = (AbstractLifecycleHandler)
                container.getLifecycleHandlerManager().getLifecycleHandler("bootstrap");
            assertThat(bootstrap.getId()).isEqualTo("bootstrap");
            assertThat(bootstrap.getBeginSegment()).hasSize(1);
            assertThat(container.getContext().get(PlexusConstants.PLEXUS_KEY)).isSameAs(container);
            assertThat(container.isReloadingEnabled()).isTrue();
        } finally {
            container.dispose();
        }
    }

    @Test
    void createAndAutowireInstantiatesNamedComponentFromContainerRealm() throws Exception {
        DefaultPlexusContainer container = new DefaultPlexusContainer(
            "autowire-container",
            null
        );

        try {
            Object component = container.createAndAutowire(SimpleAutowirableComponent.class.getName());

            assertThat(component).isInstanceOf(SimpleAutowirableComponent.class);
            assertThat(((SimpleAutowirableComponent) component).isConstructed()).isTrue();
        } finally {
            container.dispose();
        }
    }

    @Test
    void resourceConstructorLoadsAndConfiguresComponent() throws Exception {
        DefaultPlexusContainer container = new DefaultPlexusContainer(
            "resource-container",
            Map.of("service.message", "ready from resource"),
            "/org_codehaus_plexus/plexus_container_default/resource-container-plexus.xml"
        );

        try {
            ResourceConfiguredComponent component = (ResourceConfiguredComponent) container.lookup(
                "resource-configured-service"
            );

            assertThat(component.getMessage()).isEqualTo("ready from resource");
            assertThat(container.lookup("resource-configured-service")).isSameAs(component);
            container.release(component);
            container.release(component);
        } finally {
            container.dispose();
        }
    }

    public static final class ResourceConfiguredComponent {
        private String message;

        public void setMessage(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }

    public static final class SimpleAutowirableComponent {
        private final boolean constructed;

        public SimpleAutowirableComponent() {
            constructed = true;
        }

        public boolean isConstructed() {
            return constructed;
        }
    }
}
