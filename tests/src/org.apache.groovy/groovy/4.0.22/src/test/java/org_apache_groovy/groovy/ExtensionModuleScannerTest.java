/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import org.codehaus.groovy.runtime.m12n.ExtensionModule;
import org.codehaus.groovy.runtime.m12n.ExtensionModuleScanner;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ExtensionModuleScannerTest {
    @Test
    void discoversAnApplicationExtensionModuleOnTheClasspath() {
        List<ExtensionModule> modules = new ArrayList<>();
        new ExtensionModuleScanner(modules::add, getClass().getClassLoader()).scanClasspathModules();
        ExtensionModule module = modules.stream().filter(candidate -> "inventory-test".equals(candidate.getName()))
                .findFirst().orElseThrow();
        assertEquals("stock:book", module.getMetaMethods().stream()
                .filter(method -> "stockLabel".equals(method.getName())).findFirst().orElseThrow()
                .invoke("book", new Object[]{}));
    }

    public static class InventoryExtensions {
        public static String stockLabel(String self) {
            return "stock:" + self;
        }
    }
}
