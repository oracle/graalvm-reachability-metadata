/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package h2;

import org.graalvm.internal.tck.NativeImageSupport;
import org.h2.util.SourceCompiler;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

public class SourceCompilerAnonymous1Test {
    @Test
    void fallbackCompilerLoadsClassThroughSourceCompilerClassLoader() throws Exception {
        SourceCompiler compiler = new SourceCompiler();
        compiler.setJavaSystemCompiler(false);
        compiler.setSource("h2.generated.FallbackCompiledFunction",
                "int add(int left, int right) { return left + right; }");

        try {
            Method method = compiler.getMethod("h2.generated.FallbackCompiledFunction");

            assertThat(method.invoke(null, 19, 23)).isEqualTo(42);
        } catch (Error error) {
            rethrowIfNotNativeImageDynamicClassLoadingError(error);
        }
    }

    private static void rethrowIfNotNativeImageDynamicClassLoadingError(Error error) {
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
            throw error;
        }
    }
}
