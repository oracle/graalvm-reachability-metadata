/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_janino.commons_compiler;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.codehaus.commons.compiler.util.reflect.Proxies;
import org.junit.jupiter.api.Test;

public class ProxiesTest {
    @Test
    public void delegatesInterfaceMethodToConfiguredDelegateMethod() throws NoSuchMethodException {
        GreetingDelegate delegate = new GreetingDelegate();
        Method interfaceMethod = Greeting.class.getMethod("greet", String.class);
        Method delegateMethod = GreetingDelegate.class.getDeclaredMethod("sayHello", String.class);

        Greeting greeting = Proxies.newInstance(delegate, interfaceMethod, delegateMethod);
        String message = greeting.greet("Janino");

        assertThat(message).isEqualTo("Hello, Janino");
    }

    public interface Greeting {
        String greet(String name);
    }

    private static final class GreetingDelegate {
        private String sayHello(String name) {
            return "Hello, " + name;
        }
    }
}
