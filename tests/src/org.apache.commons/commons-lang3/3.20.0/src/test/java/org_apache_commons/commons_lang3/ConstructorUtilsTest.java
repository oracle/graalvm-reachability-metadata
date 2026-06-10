/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_lang3;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;

import org.apache.commons.lang3.reflect.ConstructorUtils;
import org.junit.jupiter.api.Test;

public class ConstructorUtilsTest {

    @Test
    public void getAccessibleConstructorAndInvokeExactConstructorUseExactPublicLookup() throws Exception {
        Constructor<ExactConstructorTarget> constructor = ConstructorUtils.getAccessibleConstructor(
                ExactConstructorTarget.class, String.class);
        ExactConstructorTarget target = ConstructorUtils.invokeExactConstructor(ExactConstructorTarget.class, "commons");

        assertThat(constructor).isNotNull();
        assertThat(constructor.getDeclaringClass()).isEqualTo(ExactConstructorTarget.class);
        assertThat(constructor.getParameterTypes()).containsExactly(String.class);
        assertThat(target.describe()).isEqualTo("exact:commons");
    }

    @Test
    public void getMatchingAccessibleConstructorUsesDirectLookupForExactSignature() {
        Constructor<ExactConstructorTarget> constructor = ConstructorUtils.getMatchingAccessibleConstructor(
                ExactConstructorTarget.class, String.class);

        assertThat(constructor).isNotNull();
        assertThat(constructor.getDeclaringClass()).isEqualTo(ExactConstructorTarget.class);
        assertThat(constructor.getParameterTypes()).containsExactly(String.class);
    }

    @Test
    public void invokeConstructorUsesCompatibleAccessibleConstructorWhenExactSignatureIsMissing() throws Exception {
        Constructor<AssignableConstructorTarget> constructor = ConstructorUtils.getMatchingAccessibleConstructor(
                AssignableConstructorTarget.class, Integer.class);
        AssignableConstructorTarget target = ConstructorUtils.invokeConstructor(AssignableConstructorTarget.class,
                Integer.valueOf(7));

        assertThat(constructor).isNotNull();
        assertThat(constructor.getDeclaringClass()).isEqualTo(AssignableConstructorTarget.class);
        assertThat(constructor.getParameterTypes()).containsExactly(Number.class);
        assertThat(target.describe()).isEqualTo("number:7");
    }

    public static class ExactConstructorTarget {
        private final String value;

        public ExactConstructorTarget(String value) {
            this.value = value;
        }

        public String describe() {
            return "exact:" + value;
        }
    }

    public static class AssignableConstructorTarget {
        private final Number value;

        public AssignableConstructorTarget(Number value) {
            this.value = value;
        }

        public String describe() {
            return "number:" + value;
        }
    }
}
