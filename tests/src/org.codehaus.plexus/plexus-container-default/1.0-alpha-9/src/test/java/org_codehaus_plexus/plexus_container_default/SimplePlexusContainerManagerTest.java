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
import org.codehaus.plexus.SimplePlexusContainerManager;
import org.codehaus.plexus.component.configurator.BasicComponentConfigurator;
import org.codehaus.plexus.configuration.xml.XmlPlexusConfiguration;
import org.codehaus.plexus.context.DefaultContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SimplePlexusContainerManagerTest {
    private static final String MANAGED_CONTAINER_CONFIGURATION =
        "org_codehaus_plexus/plexus_container_default/simple-manager-plexus.xml";

    @Test
    public void contextualizeExposesManagedContainerSlot() throws Exception {
        SimplePlexusContainerManager manager = new SimplePlexusContainerManager();

        manager.contextualize(contextWithParentContainer());

        PlexusContainer[] managedContainers = manager.getManagedContainers();
        assertThat(managedContainers).hasSize(1);
        assertThat(managedContainers[0]).isNull();
    }

    @Test
    public void initializeLoadsConfiguredResourceFromContextClassLoader() throws Exception {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        SimplePlexusContainerManager manager = new SimplePlexusContainerManager();

        try {
            Thread.currentThread().setContextClassLoader(testClassLoader());
            configureResource(manager);
            manager.contextualize(contextWithParentContainer());

            manager.initialize();

            PlexusContainer managedContainer = manager.getManagedContainers()[0];
            assertThat(managedContainer).isInstanceOf(DefaultPlexusContainer.class);
            assertThat(((DefaultPlexusContainer) managedContainer).isInitialized()).isTrue();
        } finally {
            PlexusContainer managedContainer = manager.getManagedContainers()[0];
            if (managedContainer != null && managedContainer.isInitialized()) {
                manager.stop();
            }
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    private static void configureResource(SimplePlexusContainerManager manager) throws Exception {
        XmlPlexusConfiguration configuration = new XmlPlexusConfiguration("configuration");
        XmlPlexusConfiguration plexusConfig = new XmlPlexusConfiguration("plexus-config");
        plexusConfig.setValue(MANAGED_CONTAINER_CONFIGURATION);
        configuration.addChild(plexusConfig);

        new BasicComponentConfigurator().configureComponent(manager, configuration, testRealm());
    }

    private static ClassRealm testRealm() throws Exception {
        ClassWorld classWorld = new ClassWorld();
        return classWorld.newRealm("simple-manager", testClassLoader());
    }

    private static ClassLoader testClassLoader() {
        return SimplePlexusContainerManagerTest.class.getClassLoader();
    }

    private static DefaultContext contextWithParentContainer() {
        DefaultContext context = new DefaultContext();
        context.put(PlexusConstants.PLEXUS_KEY, new DefaultPlexusContainer());
        return context;
    }
}
