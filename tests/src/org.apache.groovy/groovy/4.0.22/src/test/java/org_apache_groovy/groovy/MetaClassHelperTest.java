/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import groovy.lang.Closure;
import org.codehaus.groovy.runtime.MetaClassHelper;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.codehaus.groovy.runtime.GeneratedClosure;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Function;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class MetaClassHelperTest {
    @Test
    void buildsPrimitiveAndCommonComponentArrays() {
        assertArrayEquals(new int[]{4, 9}, (int[]) MetaClassHelper.asPrimitiveArray(List.of(4, 9), int[].class));
        Number[] values = (Number[]) MetaClassHelper.makeCommonArray(new Object[]{"prefix", 4, 2.5}, 1, Number.class);
        assertEquals(Number[].class, values.getClass());
        assertArrayEquals(new Number[]{4, 2.5}, values);
    }

    @Test
    void selectsTheSamOverloadMatchingTheClosureArity() {
        assertEquals("north:south", InvokerHelper.invokeMethod(new Overloads(), "apply", new Object[]{new PairClosure()}));
    }

    public static class PairClosure extends Closure<String> implements GeneratedClosure {
        public PairClosure() {
            super(null);
        }

        public String doCall(String first, String second) {
            return first + ":" + second;
        }
    }

    public static class Overloads {
        public String apply(Function<String, String> function) {
            return function.apply("single");
        }

        public String apply(BiFunction<String, String, String> function) {
            return function.apply("north", "south");
        }
    }
}
