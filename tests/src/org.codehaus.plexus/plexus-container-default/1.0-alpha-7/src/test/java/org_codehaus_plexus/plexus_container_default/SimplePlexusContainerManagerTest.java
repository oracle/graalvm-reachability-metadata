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
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluationException;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluator;
import org.codehaus.plexus.configuration.xml.XmlPlexusConfiguration;
import org.codehaus.plexus.context.DefaultContext;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.InitializationException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class SimplePlexusContainerManagerTest {
    private static final String MANAGED_CONFIGURATION_RESOURCE = "simple-manager-managed-plexus.xml";

    private static final String BOOTSTRAP_CONFIGURATION = """
        <plexus/>
        """;

    private static final String MALFORMED_MANAGED_CONFIGURATION = """
        <plexus>
        """;

    @Test
    public void initializeLoadsManagedContainerConfigurationFromContextClassLoader() throws Exception {
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        ResourceBackedClassLoader resourceBackedClassLoader = new ResourceBackedClassLoader(originalContextClassLoader);
        SimplePlexusContainerManager manager = new SimplePlexusContainerManager();

        configureManager(manager);
        manager.contextualize(contextWithParentContainer());
        Thread.currentThread().setContextClassLoader(resourceBackedClassLoader);

        try {
            assertThatThrownBy(manager::initialize)
                .isInstanceOf(InitializationException.class)
                .hasMessage("Error initializing container");
            assertThat(resourceBackedClassLoader.wasManagedConfigurationRequested()).isTrue();
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
            disposeIfInitialized(manager);
        }
    }

    private static void configureManager(SimplePlexusContainerManager manager) throws Exception {
        XmlPlexusConfiguration configuration = new XmlPlexusConfiguration("configuration");
        XmlPlexusConfiguration plexusConfig = new XmlPlexusConfiguration("plexus-config");
        plexusConfig.setValue(MANAGED_CONFIGURATION_RESOURCE);
        configuration.addChild(plexusConfig);

        ClassWorld classWorld = new ClassWorld();
        ClassRealm realm = classWorld.newRealm(
            "simple-plexus-container-manager-test",
            SimplePlexusContainerManagerTest.class.getClassLoader()
        );
        new BasicComponentConfigurator().configureComponent(manager, configuration, new LiteralExpressionEvaluator(), realm);
    }

    private static DefaultContext contextWithParentContainer() {
        DefaultContext context = new DefaultContext();
        context.put(PlexusConstants.PLEXUS_KEY, new DefaultPlexusContainer());
        return context;
    }

    private static void disposeIfInitialized(SimplePlexusContainerManager manager) {
        PlexusContainer[] managedContainers = manager.getManagedContainers();
        if (managedContainers.length == 1
            && managedContainers[0] != null
            && managedContainers[0].isInitialized()) {
            manager.stop();
        }
    }

    private static final class LiteralExpressionEvaluator implements ExpressionEvaluator {
        @Override
        public Object evaluate(String expression) throws ExpressionEvaluationException {
            return expression;
        }

        @Override
        public File alignToBaseDirectory(File file) {
            return file;
        }
    }

    private static final class ResourceBackedClassLoader extends ClassLoader {
        private boolean managedConfigurationRequested;

        private ResourceBackedClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            if (DefaultPlexusContainer.BOOTSTRAP_CONFIGURATION.equals(name)) {
                return streamFor(BOOTSTRAP_CONFIGURATION);
            }
            if (MANAGED_CONFIGURATION_RESOURCE.equals(name)) {
                managedConfigurationRequested = true;
                return streamFor(MALFORMED_MANAGED_CONFIGURATION);
            }
            return super.getResourceAsStream(name);
        }

        private boolean wasManagedConfigurationRequested() {
            return managedConfigurationRequested;
        }

        private static ByteArrayInputStream streamFor(String value) {
            return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
        }
    }
}
