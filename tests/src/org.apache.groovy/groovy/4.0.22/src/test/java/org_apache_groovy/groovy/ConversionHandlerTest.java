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

import java.util.Comparator;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConversionHandlerTest {
    @Test
    void dispatchesInterfaceDefaultsAndObjectMethodsOnAMapProxy() {
        Closure<Integer> comparison = new Closure<Integer>(this) {
            @Override
            public Integer call(Object... arguments) {
                return ((String) arguments[0]).compareTo((String) arguments[1]);
            }
        };
        Map<String, Closure<Integer>> methods = Map.of("compare", comparison);
        Comparator<String> proxy = DefaultGroovyMethods.asType(methods, Comparator.class);
        assertTrue(proxy.compare("Ada", "Grace") < 0);
        assertTrue(proxy.reversed().compare("Ada", "Grace") > 0);
        assertTrue(proxy.equals(proxy));
        assertFalse(proxy.equals(new Object()));
        assertEquals(methods.hashCode(), proxy.hashCode());
    }
}
