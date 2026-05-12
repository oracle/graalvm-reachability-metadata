/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_velocity.velocity;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.velocity.runtime.log.Log;
import org.apache.velocity.runtime.log.NullLogSystem;
import org.apache.velocity.util.introspection.UberspectImpl;
import org.apache.velocity.util.introspection.VelMethod;
import org.junit.jupiter.api.Test;

public class UberspectImplInnerVelMethodImplTest {
    @Test
    void invokesMethodResolvedByUberspect() throws Exception {
        UberspectImpl uberspect = new UberspectImpl();
        uberspect.setLog(new Log(new NullLogSystem()));
        uberspect.init();

        GreetingTarget target = new GreetingTarget("Hello");
        Object[] arguments = {"Ada", Integer.valueOf(2)};

        VelMethod method = uberspect.getMethod(target, "repeatGreeting", arguments, null);

        assertThat(method).isNotNull();
        assertThat(method.isCacheable()).isTrue();
        assertThat(method.getMethodName()).isEqualTo("repeatGreeting");
        assertThat(method.getReturnType()).isEqualTo(String.class);
        assertThat(method.invoke(target, arguments)).isEqualTo("Hello Ada! Hello Ada!");
        assertThat(target.invocations).isEqualTo(1);
    }

    public static final class GreetingTarget {
        private final String prefix;
        private int invocations;

        public GreetingTarget(final String prefix) {
            this.prefix = prefix;
        }

        public String repeatGreeting(final String name, final int count) {
            invocations++;
            StringBuilder message = new StringBuilder();
            for (int index = 0; index < count; index++) {
                if (index > 0) {
                    message.append(' ');
                }
                message.append(prefix).append(' ').append(name).append('!');
            }
            return message.toString();
        }
    }
}
