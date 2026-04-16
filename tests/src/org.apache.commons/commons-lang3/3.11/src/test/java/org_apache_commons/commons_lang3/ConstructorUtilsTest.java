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
    void resolvesAccessibleAndMatchingConstructors() {
        final Constructor<ExactConstructorTarget> exactConstructor = ConstructorUtils.getAccessibleConstructor(
                ExactConstructorTarget.class,
                String.class
        );
        final Constructor<ExactConstructorTarget> exactMatchingConstructor = ConstructorUtils
                .getMatchingAccessibleConstructor(ExactConstructorTarget.class, String.class);
        final Constructor<AssignableConstructorTarget> matchingConstructor = ConstructorUtils
                .getMatchingAccessibleConstructor(AssignableConstructorTarget.class, StringBuilder.class);

        assertThat(exactConstructor).isNotNull();
        assertThat(exactConstructor.getDeclaringClass()).isEqualTo(ExactConstructorTarget.class);
        assertThat(exactMatchingConstructor).isNotNull();
        assertThat(exactMatchingConstructor.getParameterTypes()).containsExactly(String.class);
        assertThat(matchingConstructor).isNotNull();
        assertThat(matchingConstructor.getParameterTypes()).containsExactly(CharSequence.class);
    }

    @Test
    void invokesMatchingAndExactConstructors() throws Exception {
        final AssignableConstructorTarget matchingInstance = ConstructorUtils.invokeConstructor(
                AssignableConstructorTarget.class,
                new StringBuilder("builder")
        );
        final ExactConstructorTarget exactInstance = ConstructorUtils.invokeExactConstructor(
                ExactConstructorTarget.class,
                new Object[] {"exact"},
                new Class<?>[] {String.class}
        );

        assertThat(matchingInstance.constructorKind).isEqualTo("char-sequence");
        assertThat(matchingInstance.value).isEqualTo("builder");
        assertThat(exactInstance.value).isEqualTo("exact");
    }

    public static class ExactConstructorTarget {
        private final String value;

        public ExactConstructorTarget(final String value) {
            this.value = value;
        }
    }

    public static class AssignableConstructorTarget {
        private final String constructorKind;
        private final String value;

        public AssignableConstructorTarget(final CharSequence value) {
            this.constructorKind = "char-sequence";
            this.value = value.toString();
        }

        public AssignableConstructorTarget(final Integer value) {
            this.constructorKind = "integer";
            this.value = String.valueOf(value);
        }
    }
}
