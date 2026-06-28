/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_janino.janino;

import static org.assertj.core.api.Assertions.assertThat;

import org.codehaus.janino.ScriptEvaluator;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class ScriptEvaluatorTest {
    @Test
    void cooksAndEvaluatesParameterizedScript() throws Exception {
        try {
            final ScriptEvaluator evaluator = new ScriptEvaluator();
            evaluator.setReturnType(int.class);
            evaluator.setParameters(
                    new String[] { "left", "right" },
                    new Class<?>[] { int.class, int.class });
            evaluator.cook("return left + right;");

            final Object result = evaluator.evaluate(new Object[] { 19, 23 });

            assertThat(result).isEqualTo(42);
        } catch (Error error) {
            rethrowIfNotNativeImageDynamicClassLoadingError(error);
        }
    }

    @Test
    void createsFastEvaluatorImplementingInterface() throws Exception {
        try {
            final ScriptEvaluator evaluator = new ScriptEvaluator();
            final Object fastEvaluator = evaluator.createFastEvaluator(
                    "return prefix + value;",
                    FastScript.class,
                    new String[] { "prefix", "value" });

            final FastScript script = (FastScript) fastEvaluator;

            assertThat(script.format("value=", 7)).isEqualTo("value=7");
        } catch (Error error) {
            rethrowIfNotNativeImageDynamicClassLoadingError(error);
        }
    }

    private static void rethrowIfNotNativeImageDynamicClassLoadingError(Error error) {
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
            throw error;
        }
    }

    public interface FastScript {
        String format(String prefix, int value);
    }
}
