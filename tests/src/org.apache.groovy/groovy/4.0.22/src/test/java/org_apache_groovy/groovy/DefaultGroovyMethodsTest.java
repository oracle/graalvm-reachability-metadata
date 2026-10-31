/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import groovy.lang.Closure;
import groovy.lang.ExpandoMetaClass;
import groovy.lang.GroovyObjectSupport;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashSet;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DefaultGroovyMethodsTest {
    @Test
    void dumpsUserFieldsAndFormatsScalarArguments() {
        assertTrue(DefaultGroovyMethods.dump(new Parcel()).contains("weight=12"));
        assertEquals("weight:12", DefaultGroovyMethods.sprintf(this, "weight:%d", (Object) 12));
        assertArrayEquals(new String[]{"one", "two"},
                DefaultGroovyMethods.asType(List.of("one", "two"), String[].class));
    }

    @Test
    void coercesAnObjectTypedCollectionIntoAnArray() {
        Object values = new LinkedHashSet<>(List.of("red", "blue"));
        assertArrayEquals(new String[]{"red", "blue"}, DefaultGroovyMethods.asType(values, String[].class));
    }

    @Test
    void adaptsMapMethodsToAnInterface() {
        Closure<String> closure = new Closure<String>(this) {
            @Override
            public String call(Object... arguments) {
                return "Hello " + arguments[0];
            }
        };
        Greeting greeting = DefaultGroovyMethods.asType(Map.of("greet", closure), Greeting.class);
        assertEquals("Hello Ada", greeting.greet("Ada"));
    }

    @Test
    void coercesCollectionElementsToPrimitiveAndReferenceArrayComponents() {
        assertArrayEquals(new int[]{3, 7}, DefaultGroovyMethods.asType(List.of(3, 7), int[].class));
        assertArrayEquals(new Long[]{3L, 7L}, DefaultGroovyMethods.asType(List.of(3, 7), Long[].class));
    }

    @Test
    void installsAnInstanceMetaclassAndUpdatesItsOptimizationFlag() {
        DynamicParcel parcel = new DynamicParcel();
        ExpandoMetaClass replacement = new ExpandoMetaClass(DynamicParcel.class, false, true);
        replacement.registerBeanProperty("status", "ready");
        replacement.initialize();
        DefaultGroovyMethods.setMetaClass(parcel, replacement);
        assertTrue(DynamicParcel.__$stMC);
        assertEquals("ready", parcel.getProperty("status"));
    }

    public static class DynamicParcel extends GroovyObjectSupport {
        public static boolean __$stMC;
    }

    public interface Greeting {
        String greet(String name);
    }

    public static class Parcel {
        public int weight = 12;
    }
}
