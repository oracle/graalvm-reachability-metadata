/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import groovy.lang.DelegatingMetaClass;
import groovy.lang.GroovyObjectSupport;
import groovy.lang.MetaClassRegistry;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.codehaus.groovy.runtime.metaclass.MetaClassRegistryImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MetaClassRegistryImplTest {
    @Test
    void updatesClassDispatchAndInvalidatesPrimitiveOptimization() {
        MetaClassRegistry registry = new MetaClassRegistryImpl(MetaClassRegistryImpl.DONT_LOAD_DEFAULT);
        DelegatingMetaClass replacement = new DelegatingMetaClass(registry.getMetaClass(Model.class)) {
            @Override
            public Object getProperty(Object object, String property) {
                return "category".equals(property) ? "express" : super.getProperty(object, property);
            }
        };
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
        @SuppressWarnings("checkstyle:StaticVariableName") // Groovy's primitive-dispatch flag name.
        public static boolean __$stMC;
    }
}
