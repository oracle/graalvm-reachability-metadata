/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import org.codehaus.groovy.runtime.m12n.MetaInfExtensionModule;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MetaInfExtensionModuleTest {
    @Test
    void loadsConfiguredExtensionClassesAndInvokesTheirMethods() {
        Properties properties = new Properties();
        properties.setProperty("moduleName", "labels");
        properties.setProperty("moduleVersion", "1");
        properties.setProperty("extensionClasses", Labels.class.getName());
        MetaInfExtensionModule module = MetaInfExtensionModule.newModule(properties, getClass().getClassLoader());
        assertEquals(List.of(Labels.class), module.getInstanceMethodsExtensionClasses());
        assertEquals("item-7", module.getMetaMethods().stream()
                .filter(method -> "inventoryLabel".equals(method.getName())).findFirst().orElseThrow()
                .invoke("item", new Object[]{7}));
    }

    public static class Labels {
        public static String inventoryLabel(String self, int number) {
            return self + "-" + number;
        }
    }
}
