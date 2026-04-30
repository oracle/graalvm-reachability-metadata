/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_janino.commons_compiler;

import org.codehaus.commons.compiler.util.reflect.Classes;
import org.codehaus.commons.compiler.util.reflect.Proxies;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

public class ProxiesTest {
    @Test
    void createsProxyThatDelegatesInterfaceMethod() {
        Delegate delegate = new Delegate();
        Method interfaceMethod = Classes.getDeclaredMethod(GreetingService.class, "greet", String.class, int.class);
        Method delegateMethod = Classes.getDeclaredMethod(Delegate.class, "formatGreeting", String.class, int.class);

        GreetingService service = Proxies.newInstance(delegate, interfaceMethod, delegateMethod);

        assertThat(service.greet("Janino", 3)).isEqualTo("Hello Janino #3");
        assertThat(delegate.invocationCount()).isEqualTo(1);
    }

    public interface GreetingService {
        String greet(String name, int index);
    }

    public static final class Delegate {
        private int invocationCount;

        public String formatGreeting(String name, int index) {
            this.invocationCount++;
            return "Hello " + name + " #" + index;
        }

        int invocationCount() {
            return this.invocationCount;
        }
    }
}
