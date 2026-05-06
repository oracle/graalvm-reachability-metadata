/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aesh.aesh;

import static org.assertj.core.api.Assertions.assertThat;

import org.aesh.util.ReflectionUtil;
import org.junit.jupiter.api.Test;

public class ReflectionUtilTest {
    @Test
    void createsInstanceFromNonPublicNoArgumentConstructor() {
        NonPublicNoArgumentConstructorTarget target = ReflectionUtil.newInstance(
                NonPublicNoArgumentConstructorTarget.class);

        assertThat(target.message()).isEqualTo("created by declared constructor");
    }

    @Test
    void createsInstanceFromSingleParameterConstructorUsingParameterNoArgumentConstructor() {
        SingleParameterConstructorTarget target = ReflectionUtil.newInstance(SingleParameterConstructorTarget.class);

        assertThat(target.parameter().message()).isEqualTo("created parameter");
    }

    public static class SingleParameterConstructorTarget {
        private final ConstructorParameter parameter;

        public SingleParameterConstructorTarget(ConstructorParameter parameter) {
            this.parameter = parameter;
        }

        private ConstructorParameter parameter() {
            return parameter;
        }
    }

    public static class ConstructorParameter {
        public ConstructorParameter() {
        }

        private String message() {
            return "created parameter";
        }
    }

    private static class NonPublicNoArgumentConstructorTarget {
        private final String message;

        private NonPublicNoArgumentConstructorTarget() {
            this.message = "created by declared constructor";
        }

        private String message() {
            return message;
        }
    }
}
