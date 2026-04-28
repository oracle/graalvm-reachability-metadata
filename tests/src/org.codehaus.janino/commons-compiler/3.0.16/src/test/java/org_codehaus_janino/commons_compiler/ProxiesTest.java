/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_janino.commons_compiler;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import org.codehaus.commons.compiler.util.reflect.Proxies;
import org.junit.jupiter.api.Test;

public class ProxiesTest {

    @Test
    void createsAProxyBackedByTheDelegateMethod() throws Exception {
        Method contractMethod = Greeting.class.getMethod("greet", String.class);
        Method delegateMethod = GreetingDelegate.class.getDeclaredMethod("greet", String.class);

        Greeting proxy = Proxies.newInstance(new GreetingDelegate(), contractMethod, delegateMethod);

        assertThat(Proxy.isProxyClass(proxy.getClass())).isTrue();
    }

    public interface Greeting {
        String greet(String name);
    }

    public static final class GreetingDelegate {
        private String greet(String name) {
            return "Hello " + name;
        }
    }
}
