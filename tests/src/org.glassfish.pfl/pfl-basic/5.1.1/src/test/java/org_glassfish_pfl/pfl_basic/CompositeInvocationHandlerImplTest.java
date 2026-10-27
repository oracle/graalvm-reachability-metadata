/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_pfl.pfl_basic;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;

import org.glassfish.pfl.basic.proxy.CompositeInvocationHandlerImpl;
import org.junit.jupiter.api.Test;

public class CompositeInvocationHandlerImplTest {
    public interface Greeting {
        String greet();
    }

    @Test
    public void dispatchesInvocationsThroughProxy() {
        CompositeInvocationHandlerImpl handler = new CompositeInvocationHandlerImpl();
        handler.addInvocationHandler(Greeting.class, (proxy, method, args) -> "hello");
        Greeting greeting = (Greeting) Proxy.newProxyInstance(
                Greeting.class.getClassLoader(), new Class<?>[] {Greeting.class}, handler);

        assertThat(greeting.greet()).isEqualTo("hello");
        assertThat(greeting.toString()).contains("CompositeInvocationHandlerImpl");
    }
}
