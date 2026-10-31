/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import groovy.lang.GroovyInterceptable;
import groovy.lang.GroovyObjectSupport;
import org.codehaus.groovy.runtime.ReflectionMethodInvoker;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ReflectionMethodInvokerTest {
    @Test
    void callsTheConcreteMethodWithoutInvokingTheInterceptor() {
        Intercepted object = new Intercepted();
        assertEquals("intercepted", object.invokeMethod("greet", new Object[]{"Ada"}));
        assertEquals("Hello Ada", ReflectionMethodInvoker.invoke(object, "greet", new Object[]{"Ada"}));
    }

    public static class Intercepted extends GroovyObjectSupport implements GroovyInterceptable {
        public String greet(String name) {
            return "Hello " + name;
        }

        @Override
        public Object invokeMethod(String name, Object arguments) {
            return "intercepted";
        }
    }
}
