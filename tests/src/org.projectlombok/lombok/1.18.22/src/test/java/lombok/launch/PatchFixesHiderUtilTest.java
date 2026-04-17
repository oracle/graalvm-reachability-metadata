/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package lombok.launch;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

class PatchFixesHiderUtilTest {
    @Test
    void shadowLoadsClassesAndInvokesMethodsFoundByDeclaredSignature() {
        Class<?> loadedType = PatchFixesHider.Util.shadowLoadClass(SampleTargets.class.getName());

        assertThat(loadedType).isSameAs(SampleTargets.class);

        Method concatenate = PatchFixesHider.Util.findMethod(loadedType, "concatenate", String.class, int.class);

        assertThat(PatchFixesHider.Util.invokeMethod(concatenate, "value-", 7)).isEqualTo("value-7");
    }

    @Test
    void findsMethodsByParameterTypeNamesAndAnyArgumentCount() {
        Method multiply = PatchFixesHider.Util.findMethod(SampleTargets.class, "multiply", "int", "int");
        Method marker = PatchFixesHider.Util.findMethodAnyArgs(SampleTargets.class, "marker");

        assertThat(PatchFixesHider.Util.invokeMethod(multiply, 6, 7)).isEqualTo(42);
        assertThat(PatchFixesHider.Util.invokeMethod(marker)).isEqualTo("marker");
    }

    public static final class SampleTargets {
        public static String concatenate(String prefix, int number) {
            return prefix + number;
        }

        public static int multiply(int left, int right) {
            return left * right;
        }

        public static String marker() {
            return "marker";
        }
    }
}
