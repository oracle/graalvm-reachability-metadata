/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_janino.janino;

import static org.assertj.core.api.Assertions.assertThat;

import org.codehaus.janino.ExpressionEvaluator;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class ReflectionIClassInnerReflectionIFieldTest {
    @Test
    void foldsStaticFinalPrimitiveFieldFromReflectedClass() throws Exception {
        try {
            final ExpressionEvaluator evaluator = new ExpressionEvaluator();
            evaluator.setExpressionType(int.class);
            evaluator.cook("""
                    org_codehaus_janino.janino.ReflectionIClassInnerReflectionIFieldTest.Constants.VALUE + 7
                    """);

            final Object result = evaluator.evaluate(new Object[0]);

            assertThat(result).isEqualTo(49);
        } catch (Error error) {
            rethrowIfNotNativeImageDynamicClassLoadingError(error);
        }
    }

    public static final class Constants {
        public static final int VALUE = 42;

        private Constants() {
        }
    }

    private static void rethrowIfNotNativeImageDynamicClassLoadingError(Error error) {
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
            throw error;
        }
    }
}
