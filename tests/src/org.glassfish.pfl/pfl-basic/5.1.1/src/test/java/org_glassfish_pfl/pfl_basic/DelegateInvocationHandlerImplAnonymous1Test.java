/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_pfl.pfl_basic;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.glassfish.pfl.basic.proxy.DelegateInvocationHandlerImpl;
import org.junit.jupiter.api.Test;

public class DelegateInvocationHandlerImplAnonymous1Test {
    public static class Greeter {
        public String greet(String name) {
            return "hello " + name;
        }
    }

    @Test
    public void delegatesMethodInvocationToTarget() throws Throwable {
        Greeter target = new Greeter();
        Method method = Greeter.class.getMethod("greet", String.class);

        assertThat(DelegateInvocationHandlerImpl.create(target).invoke(target, method, new Object[] {"visitor"}))
                .isEqualTo("hello visitor");
    }
}
