/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jdbi.jdbi3_core;

import org.jdbi.v3.core.internal.JdbiClassUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JdbiClassUtilsTest {
    @Test
    void discoversMethodsAndCreatesInstances() {
        assertThat(JdbiClassUtils.isPresent(ConstructedValue.class.getName())).isTrue();
        assertThat(JdbiClassUtils.methodLookup(ConstructedValue.class, "value").getName()).isEqualTo("value");
        assertThat(JdbiClassUtils.methodLookup(ConstructedValue.class, "hidden").getName()).isEqualTo("hidden");
        assertThat(JdbiClassUtils.safeMethodLookup(ConstructedValue.class, "value")).isPresent();
        assertThat(JdbiClassUtils.safeMethodLookup(ConstructedValue.class, "hidden")).isPresent();

        EmptyValue empty = JdbiClassUtils.checkedCreateInstance(EmptyValue.class);
        ConstructedValue value = JdbiClassUtils.findConstructor(
                        ConstructedValue.class, String.class, int.class)
                .invoke(handle -> handle.invokeWithArguments("created", 9));

        assertThat(empty.ready()).isTrue();
        assertThat(value.value()).isEqualTo("created-9");
    }

    public static class EmptyValue {
        public EmptyValue() { }

        public boolean ready() {
            return true;
        }
    }

    public static class ConstructedValue {
        private final String value;

        public ConstructedValue(String text, int number) {
            value = text + "-" + number;
        }

        public String value() {
            return value;
        }

        private String hidden() {
            return value;
        }
    }
}
