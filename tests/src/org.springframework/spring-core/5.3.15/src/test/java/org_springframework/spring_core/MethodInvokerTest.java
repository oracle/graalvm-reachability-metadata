/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.util.MethodInvoker;

/** Verifies preparation and reflective invocation of a public method. */
public class MethodInvokerTest {
    @Test
    void invokesConfiguredTargetMethod() throws Exception {
        MethodInvoker invoker = new MethodInvoker();
        invoker.setTargetObject(new GreetingService());
        invoker.setTargetMethod("greet");
        invoker.setArguments("Spring");

        invoker.prepare();

        assertThat(invoker.invoke()).isEqualTo("Hello Spring");
    }

    public static final class GreetingService {
        public String greet(String name) {
            return "Hello " + name;
        }
    }
}
