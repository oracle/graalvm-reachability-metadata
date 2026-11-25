/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import org.codehaus.groovy.vmplugin.v9.Java9;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class Java9Test {
    @Test
    void invokesAnInterfaceDefaultImplementationUsingAPrivateLookup() throws Throwable {
        Java9 plugin = new Java9();
        Method method = Greeting.class.getMethod("greeting");
        Object handle = plugin.getInvokeSpecialHandle(method, new CustomizedGreeting());
        assertEquals("hello from interface", plugin.invokeHandle(handle, new Object[0]));
    }

    public interface Greeting {
        default String greeting() {
            return "hello from interface";
        }
    }

    public static class CustomizedGreeting implements Greeting {
        @Override
        public String greeting() {
            return "hello from class";
        }
    }
}
