/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import groovy.lang.ExpandoMetaClass;
import groovy.lang.GroovyObjectSupport;
import groovy.lang.GroovySystem;
import groovy.lang.MetaClassRegistry;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MetaClassRegistryImplTest {
    @Test
    void updatesClassDispatchAndInvalidatesPrimitiveOptimization() {
        MetaClassRegistry registry = GroovySystem.getMetaClassRegistry();
        ExpandoMetaClass replacement = new ExpandoMetaClass(Model.class, false, true);
        replacement.registerBeanProperty("category", "express");
        replacement.initialize();
        try {
            registry.setMetaClass(Model.class, replacement);
            assertTrue(Model.__$stMC);
            assertEquals("express", InvokerHelper.getProperty(new Model(), "category"));
        } finally {
            registry.removeMetaClass(Model.class);
        }
        assertFalse(Model.__$stMC);
    }

    public static class Model extends GroovyObjectSupport {
        public static boolean __$stMC;
    }
}
