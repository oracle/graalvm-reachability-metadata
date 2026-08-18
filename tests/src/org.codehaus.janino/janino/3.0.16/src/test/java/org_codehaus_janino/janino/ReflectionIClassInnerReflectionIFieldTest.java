/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_janino.janino;

import static org.assertj.core.api.Assertions.assertThat;

import org.codehaus.janino.ExpressionEvaluator;
import org.junit.jupiter.api.Test;

public class ReflectionIClassInnerReflectionIFieldTest {
    public static final int COMPILED_CONSTANT = 34;

    @Test
    void readsStaticFinalFieldAsConstantDuringExpressionCompilation() throws Exception {
        try {
            final ExpressionEvaluator evaluator = new ExpressionEvaluator();
            evaluator.setExpressionType(int.class);
            evaluator.cook(ReflectionIClassInnerReflectionIFieldTest.class.getName() + ".COMPILED_CONSTANT + 8");

            final Object result = evaluator.evaluate(new Object[0]);

            assertThat(result).isEqualTo(42);
        } catch (Throwable throwable) {
            NativeImageDynamicClassLoadingSupport.rethrowIfNotNativeImageDynamicClassLoadingFailure(throwable);
        }
    }
}
