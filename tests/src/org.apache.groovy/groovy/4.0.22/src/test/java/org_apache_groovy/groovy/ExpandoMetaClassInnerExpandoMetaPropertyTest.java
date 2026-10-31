/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import groovy.lang.Closure;
import groovy.lang.ExpandoMetaClass;
import groovy.lang.GroovyObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ExpandoMetaClassInnerExpandoMetaPropertyTest {
    @Test
    void appendsAndInvokesAnInstanceMethodWithoutChangingTheGlobalRegistry() {
        ExpandoMetaClass metaClass = new ExpandoMetaClass(Customer.class, false, true);
        metaClass.initialize();
        Closure<String> greeting = new Closure<String>(this) {
            public String doCall(String name) {
                return "Welcome " + name;
            }
        };
        GroovyObject property = (GroovyObject) metaClass.getProperty("welcome");
        property.invokeMethod("leftShift", new Object[]{greeting});
        assertEquals("Welcome Ada", metaClass.invokeMethod(new Customer(), "welcome", new Object[]{"Ada"}));
    }

    public static class Customer {
    }
}
