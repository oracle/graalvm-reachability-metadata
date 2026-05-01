/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_antlr.ST4;

import org.junit.jupiter.api.Test;
import org.stringtemplate.v4.misc.ObjectModelAdaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ObjectModelAdaptorTest {
    @Test
    void readsJavaBeanPropertyThroughGetter() {
        final ObjectModelAdaptor adaptor = new ObjectModelAdaptor();
        final MethodBackedView view = new MethodBackedView("getter value");

        final Object propertyValue = adaptor.getProperty(null, null, view, "displayName", "displayName");

        assertEquals("getter value", propertyValue);
    }

    @Test
    void readsPropertyThroughPublicFieldWhenNoGetterExists() {
        final ObjectModelAdaptor adaptor = new ObjectModelAdaptor();
        final FieldBackedView view = new FieldBackedView("field value");

        final Object propertyValue = adaptor.getProperty(null, null, view, "status", "status");

        assertEquals("field value", propertyValue);
    }

    public static final class MethodBackedView {
        private final String displayName;

        MethodBackedView(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public static final class FieldBackedView {
        public final String status;

        FieldBackedView(String status) {
            this.status = status;
        }
    }
}
