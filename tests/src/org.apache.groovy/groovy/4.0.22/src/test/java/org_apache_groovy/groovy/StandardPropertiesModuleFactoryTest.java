/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import org.codehaus.groovy.runtime.m12n.ExtensionModule;
import org.codehaus.groovy.runtime.m12n.MetaInfExtensionModule;
import org.codehaus.groovy.runtime.m12n.PropertiesModuleFactory;
import org.codehaus.groovy.runtime.m12n.StandardPropertiesModuleFactory;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class StandardPropertiesModuleFactoryTest {
    @Test
    void delegatesToAConfiguredModuleFactory() {
        Properties properties = new Properties();
        properties.setProperty("moduleName", "application-module");
        properties.setProperty("moduleVersion", "1");
        properties.setProperty("moduleFactory", ApplicationFactory.class.getName());
        ExtensionModule module = new StandardPropertiesModuleFactory().newModule(properties, getClass().getClassLoader());
        assertEquals("application-module", module.getName());
        assertEquals("delegated", module.getVersion());
    }

    public static class ApplicationFactory extends PropertiesModuleFactory {
        @Override
        public ExtensionModule newModule(Properties properties, ClassLoader loader) {
            Properties delegated = new Properties();
            delegated.putAll(properties);
            delegated.setProperty("moduleVersion", "delegated");
            return MetaInfExtensionModule.newModule(delegated, loader);
        }
    }
}
