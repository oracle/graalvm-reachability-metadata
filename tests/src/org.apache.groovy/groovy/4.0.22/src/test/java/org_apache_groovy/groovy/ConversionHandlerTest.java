/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import groovy.lang.Closure;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConversionHandlerTest {
    @Test
    void dispatchesInterfaceDefaultsAndObjectMethodsOnAMapProxy() {
        Closure<String> name = new Closure<String>(this) {
            public String doCall() {
                return "Ada";
            }
        };
        Named proxy = DefaultGroovyMethods.asType(Map.of("name", name), Named.class);
        assertEquals("Hello Ada", proxy.greeting());
        assertTrue(proxy.equals(proxy));
        assertFalse(proxy.equals(new Object()));
        assertEquals(proxy.hashCode(), proxy.hashCode());
    }

    public interface Named {
        String name();

        default String greeting() {
            return "Hello " + name();
        }
    }
}
