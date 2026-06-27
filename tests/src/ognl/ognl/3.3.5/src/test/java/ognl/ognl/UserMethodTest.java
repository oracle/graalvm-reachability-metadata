/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ognl.ognl;

import ognl.security.UserMethod;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

public class UserMethodTest {
    @Test
    void invokesSuppliedUserMethodWithArguments() throws Exception {
        final UserMethodFixture target = new UserMethodFixture("OGNL");
        final Method method = UserMethodFixture.class.getMethod("message", String.class, int.class);
        final UserMethod userMethod = new UserMethod(target, method, new Object[] {"hello", 3});

        final Object result = userMethod.run();

        assertThat(result).isEqualTo("hello OGNL 3");
    }

    public static final class UserMethodFixture {
        private final String name;

        public UserMethodFixture(String name) {
            this.name = name;
        }

        public String message(String prefix, int count) {
            return prefix + " " + name + " " + count;
        }
    }
}
