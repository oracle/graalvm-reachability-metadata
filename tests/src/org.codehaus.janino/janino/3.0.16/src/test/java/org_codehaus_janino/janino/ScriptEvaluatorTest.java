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
    void compilesAndEvaluatesParameterizedScript() throws Exception {
        try {
            final ScriptEvaluator evaluator = new ScriptEvaluator();
            evaluator.setReturnType(int.class);
            evaluator.setParameters(
                    new String[] { "left", "right" },
                    new Class<?>[] { int.class, int.class }
            );
            evaluator.cook("return left + right;");

            final Object result = evaluator.evaluate(new Object[] { 3, 4 });

            assertThat(result).isEqualTo(7);
        } catch (Error error) {
            rethrowIfNotNativeImageDynamicClassLoadingError(error);
        }
    }

    @Test
    void createsFastEvaluatorForSingleMethodInterface() throws Exception {
        try {
            final ScriptEvaluator evaluator = new ScriptEvaluator();

            final IntCombiner combiner = (IntCombiner) evaluator.createFastEvaluator(
                    "return first * 10 + second;",
                    IntCombiner.class,
                    new String[] { "first", "second" }
            );

            assertThat(combiner.combine(6, 5)).isEqualTo(65);
        } catch (Error error) {
            rethrowIfNotNativeImageDynamicClassLoadingError(error);
        }
    }

    public interface IntCombiner {
        int combine(int first, int second);
    }

    private static void rethrowIfNotNativeImageDynamicClassLoadingError(Error error) {
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
            throw error;
        }
    }
}
