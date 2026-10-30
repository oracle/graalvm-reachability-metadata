/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import org.codehaus.groovy.runtime.InvokerHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CachedMethodTest {
    @Test
    void invokesPublicInstanceAndStaticMethods() {
        assertEquals("invoice-8", InvokerHelper.invokeMethod(new Labels(), "label", new Object[]{8}));
        assertEquals(18, InvokerHelper.invokeStaticMethod(Labels.class, "doubleValue", new Object[]{9}));
    }

    public static class Labels {
        public String label(int number) {
            return "invoice-" + number;
        }

        public static int doubleValue(int value) {
            return value * 2;
        }
    }
}
