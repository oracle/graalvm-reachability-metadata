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

public class ProxiesAnonymous1Test {

    @Test
    void dispatchesInvocationsThroughTheGeneratedInvocationHandler() throws Exception {
        Method contractMethod = Greeting.class.getMethod("greet", String.class);
        Method delegateMethod = GreetingDelegate.class.getDeclaredMethod("greet", String.class);
        Greeting proxy = Proxies.newInstance(new GreetingDelegate(), contractMethod, delegateMethod);

        assertThat(proxy.greet("Native Image")).isEqualTo("Hello Native Image");
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
