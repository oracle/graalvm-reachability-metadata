/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_esotericsoftware.kryo_shaded;

import com.esotericsoftware.reflectasm.MethodAccess;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MethodAccessTest {
    @Test
    void invokesPublicMethodsAndInheritedMethodsByIndex() {
        MethodTarget target = new MethodTarget("prefix");
        MethodAccess access = MethodAccess.get(MethodTarget.class);

        int combineIndex = access.getIndex("combine", String.class, int.class);
        int describeIndex = access.getIndex("describe");

        Object combined = access.invoke(target, combineIndex, "value", 2);
        Object described = access.invoke(target, describeIndex);

        assertThat(combined).isEqualTo("prefix:value:value");
        assertThat(described).isEqualTo("base:prefix");
        assertThat(access.getMethodNames()).contains("combine", "describe");
    }

    @Test
    void invokesOverloadedMethodsUsingNameAndParameterTypes() {
        MethodTarget target = new MethodTarget("seed");
        MethodAccess access = MethodAccess.get(MethodTarget.class);

        Object noArgumentResult = access.invoke(target, "overloaded", new Class<?>[0]);
        Object textResult = access.invoke(target, "overloaded", new Class<?>[] {String.class}, "branch");
        Object countResult = access.invoke(target, "overloaded", new Class<?>[] {int.class}, 3);

        assertThat(noArgumentResult).isEqualTo("seed");
        assertThat(textResult).isEqualTo("seed-branch");
        assertThat(countResult).isEqualTo(12);
        assertThat(access.getParameterTypes()[access.getIndex("overloaded", int.class)]).containsExactly(int.class);
        assertThat(access.getReturnTypes()[access.getIndex("overloaded", int.class)]).isEqualTo(int.class);
    }

    public static class MethodBase {
        public String describe() {
            return "base:" + value();
        }

        protected String value() {
            return "base";
        }
    }

    public static class MethodTarget extends MethodBase {
        private final String prefix;

        public MethodTarget(String prefix) {
            this.prefix = prefix;
        }

        public String combine(String value, int repetitions) {
            StringBuilder result = new StringBuilder(prefix);
            for (int i = 0; i < repetitions; i++) {
                result.append(':').append(value);
            }
            return result.toString();
        }

        public String overloaded() {
            return prefix;
        }

        public String overloaded(String suffix) {
            return prefix + '-' + suffix;
        }

        public int overloaded(int multiplier) {
            return prefix.length() * multiplier;
        }

        @Override
        protected String value() {
            return prefix;
        }

        @SuppressWarnings("unused")
        private String hidden() {
            return "hidden";
        }
    }
}
