/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.dubbo;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.dubbo.common.utils.ReflectUtils;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

public class ReflectUtilsTest {
    @Test
    void resolvesDescriptorsMethodsConstructorsAndEmptyObjects() throws Exception {
        assertThat(ReflectUtils.name2class("java.lang.String[]")).isEqualTo(String[].class);
        assertThat(ReflectUtils.desc2class("[[Ljava/lang/String;")).isEqualTo(String[][].class);

        Method method = ReflectUtils.findMethodByMethodSignature(Sample.class, "accept", null);
        assertThat(method.getReturnType()).isEqualTo(String.class);
        assertThat(ReflectUtils.findMethodByMethodSignature(
                        Sample.class, "accept", new String[] {"java.lang.String"}))
                .isEqualTo(method);

        Constructor<?> constructor = ReflectUtils.findConstructor(Sample.class, String.class);
        assertThat(constructor.getParameterTypes()).containsExactly(CharSequence.class);

        EmptyObject empty = (EmptyObject) ReflectUtils.getEmptyObject(EmptyObject.class);
        assertThat(empty.text).isEmpty();
        assertThat(empty.values).isEmpty();
    }

    public static class Sample {
        private final String value;

        public Sample() {
            this("");
        }

        public Sample(CharSequence value) {
            this.value = value.toString();
        }

        public String accept(String input) {
            return input;
        }
    }

    public static class EmptyObject {
        private String text;
        private int[] values;
    }
}
