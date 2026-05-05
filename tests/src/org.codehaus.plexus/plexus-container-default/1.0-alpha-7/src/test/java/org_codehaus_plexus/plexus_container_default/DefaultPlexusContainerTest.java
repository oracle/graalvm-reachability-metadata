/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus.plexus_container_default;

import org.codehaus.classworlds.ClassRealm;
import org.codehaus.classworlds.ClassWorld;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusConstants;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.logging.console.ConsoleLoggerManager;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultPlexusContainerTest {
    private static final String BOOTSTRAP_CONFIGURATION = """
        <plexus>
          <resources/>
          <components/>
          <component-repository implementation="org.codehaus.plexus.component.repository.DefaultComponentRepository"/>
          <lifecycle-handler-manager implementation="org.codehaus.plexus.lifecycle.DefaultLifecycleHandlerManager">
            <lifecycle-handlers>
              <lifecycle-handler implementation="org.codehaus.plexus.personality.plexus.PlexusLifecycleHandler">
                <id>plexus</id>
                <name>Plexus</name>
              </lifecycle-handler>
            </lifecycle-handlers>
          </lifecycle-handler-manager>
          <component-manager-manager
              implementation="org.codehaus.plexus.component.manager.DefaultComponentManagerManager">
            <component-managers/>
            <default-component-manager-id>singleton</default-component-manager-id>
          </component-manager-manager>
          <component-discoverer-manager
              implementation="org.codehaus.plexus.component.discovery.DefaultComponentDiscovererManager">
            <component-discoverers/>
            <listeners/>
          </component-discoverer-manager>
          <component-factory-manager
              implementation="org.codehaus.plexus.component.factory.DefaultComponentFactoryManager"/>
          <component-composer-manager
              implementation="org.codehaus.plexus.component.composition.DefaultComponentComposerManager">
            <component-composers/>
          </component-composer-manager>
          <system-properties/>
          <load-on-start/>
        </plexus>
        """;

    @Test
    void initializeAssignsLoggerForPlexusContainerRole() throws Exception {
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        DefaultPlexusContainer container = new DefaultPlexusContainer();
        ClassWorld classWorld = new ClassWorld();
        ClassRealm coreRealm = classWorld.newRealm("test.core", new BootstrapConfigurationClassLoader());

        try {
            container.setClassWorld(classWorld);
            container.setCoreRealm(coreRealm);
            container.setLoggerManager(new ConsoleLoggerManager("fatal"));

            container.initialize();

            assertThat(container.isInitialized()).isTrue();
            assertThat(container.getLogger()).isNotNull();
            assertThat(container.getContext().get(PlexusConstants.PLEXUS_KEY)).isSameAs(container);
            assertThat(container.getLogger().getName()).contains(PlexusContainer.class.getName());
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
            if (container.isInitialized()) {
                container.dispose();
            }
        }
    }

    private static final class BootstrapConfigurationClassLoader extends ClassLoader {
        private BootstrapConfigurationClassLoader() {
            super(DefaultPlexusContainerTest.class.getClassLoader());
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            if (DefaultPlexusContainer.BOOTSTRAP_CONFIGURATION.equals(name)) {
                return new ByteArrayInputStream(BOOTSTRAP_CONFIGURATION.getBytes(StandardCharsets.UTF_8));
            }
            return super.getResourceAsStream(name);
        }
    }
}
