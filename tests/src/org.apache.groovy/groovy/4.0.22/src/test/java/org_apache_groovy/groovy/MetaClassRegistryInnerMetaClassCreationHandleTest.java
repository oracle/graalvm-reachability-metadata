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
import org_apache_groovy.groovy.metaclasses.PriorityInvoice;
import org_apache_groovy.groovy.metaclasses.Receipt;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MetaClassRegistryInnerMetaClassCreationHandleTest {
    @Test
    void fallsBackToTheNormalMetaclassWhenNoCustomMetaclassExists() {
        MetaClass metaClass = new MetaClassCreationHandle().create(Invoice.class, GroovySystem.getMetaClassRegistry());
        metaClass.initialize();
        assertEquals("invoice-12", metaClass.invokeMethod(new Invoice(), "reference", new Object[]{12}));
    }

    @Test
    void discoversADelegatingCustomMetaclassUsingItsMetaClassConstructor() {
        PriorityInvoice invoice = new PriorityInvoice();
        MetaClass metaClass = new MetaClassCreationHandle().create(PriorityInvoice.class, GroovySystem.getMetaClassRegistry());
        metaClass.initialize();
        assertEquals("priority:invoice-24", metaClass.invokeMethod(invoice, "priorityReference", new Object[]{24}));
        assertEquals("invoice-25", metaClass.invokeMethod(invoice, "reference", new Object[]{25}));
    }

    @Test
    void discoversACustomMetaclassUsingItsRegistryAndClassConstructor() {
        Receipt receipt = new Receipt();
        MetaClass metaClass = new MetaClassCreationHandle().create(Receipt.class, GroovySystem.getMetaClassRegistry());
        metaClass.initialize();
        assertEquals("archived:receipt-36", metaClass.invokeMethod(receipt, "archiveReference", new Object[]{36}));
        assertEquals("receipt-37", metaClass.invokeMethod(receipt, "reference", new Object[]{37}));
    }

    public static class Invoice {
        public String reference(int number) {
            return "invoice-" + number;
        }
    }
}
