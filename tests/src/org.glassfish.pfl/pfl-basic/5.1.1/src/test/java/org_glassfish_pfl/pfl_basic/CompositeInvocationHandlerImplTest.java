/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_pfl.pfl_basic;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.glassfish.pfl.basic.proxy.CompositeInvocationHandlerImpl;
import org.junit.jupiter.api.Test;

public class CompositeInvocationHandlerImplTest {
    public interface Greeting {
        String greet();
    }

    @Test
    public void dispatchesInvocationToRegisteredHandler() throws Throwable {
        CompositeInvocationHandlerImpl handler = new CompositeInvocationHandlerImpl();
        handler.addInvocationHandler(Greeting.class, (proxy, method, args) -> "hello");
        Method method = Greeting.class.getMethod("greet");

        assertThat(handler.invoke(null, method, new Object[0])).isEqualTo("hello");
    }
}
