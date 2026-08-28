/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.core.LocalVariableTableParameterNameDiscoverer;

/** Verifies executable resolution while reading local-variable tables. */
@SuppressWarnings("deprecation")
public class LocalVariableTableParameterNameDiscovererInnerLocalVariableTableVisitorTest {
    @Test
    void resolvesMethodAndConstructorParameterNames() throws Exception {
        LocalVariableTableParameterNameDiscoverer discoverer =
                new LocalVariableTableParameterNameDiscoverer();
        Method method = ParameterFixture.class.getDeclaredMethod("format", String.class, int.class);
        Constructor<ParameterFixture> constructor =
                ParameterFixture.class.getDeclaredConstructor(String.class);

        assertThat(discoverer.getParameterNames(method)).containsExactly("value", "count");
        assertThat(discoverer.getParameterNames(constructor)).containsExactly("prefix");
    }

    public static final class ParameterFixture {
        private final String prefix;

        public ParameterFixture(String prefix) {
            this.prefix = prefix;
        }

        public String format(String value, int count) {
            return this.prefix + value.repeat(count);
        }
    }
}
