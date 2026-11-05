/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import groovy.lang.GroovySystem;
import groovy.lang.MetaClass;
import groovy.lang.MetaClassRegistry.MetaClassCreationHandle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MetaClassRegistryInnerMetaClassCreationHandleTest {
    @Test
    void fallsBackToTheNormalMetaclassWhenNoCustomMetaclassExists() {
        MetaClass metaClass = new MetaClassCreationHandle().create(Invoice.class, GroovySystem.getMetaClassRegistry());
        metaClass.initialize();
        assertEquals("invoice-12", metaClass.invokeMethod(new Invoice(), "reference", new Object[]{12}));
    }

    public static class Invoice {
        public String reference(int number) {
            return "invoice-" + number;
        }
    }
}
