/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse.osgi;

import java.nio.file.Path;
import java.util.Properties;

import org.eclipse.core.runtime.adaptor.LocationManager;
import org.eclipse.osgi.baseadaptor.BaseAdaptor;
import org.eclipse.osgi.baseadaptor.HookRegistry;
import org.eclipse.osgi.internal.baseadaptor.BaseHookConfigurator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BaseAdaptorTest {
    @Test
    public void loadsFrameworkPropertiesFromAClasspathResource(@TempDir Path directory) {
        try (FrameworkProperty configurators = new FrameworkProperty(HookRegistry.PROP_HOOK_CONFIGURATORS,
                     BaseHookConfigurator.class.getName());
             FrameworkProperty configuration = new FrameworkProperty("osgi.configuration.area", directory.toUri().toString());
             FrameworkProperty install = new FrameworkProperty("osgi.install.area", directory.toUri().toString());
             FrameworkProperty resource = new FrameworkProperty("osgi.framework.properties", "/org_eclipse/osgi/adaptor.properties")) {
            LocationManager.initializeLocations();
            BaseAdaptor adaptor = new BaseAdaptor(new String[0]);
            Properties properties = adaptor.getProperties();
            assertEquals("catalog", properties.getProperty("application.name"));
            assertEquals("enabled", properties.getProperty("application.discovery"));
        }
    }
}
