/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package h2.dynamic;

import org.graalvm.internal.tck.NativeImageSupport;
import org.h2.util.SourceCompiler;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

public class SourceCompilerTest {
    @Test
    void compilesJavaSourceWithLegacyJavacEntryPoint() throws Exception {
        SourceCompiler compiler = new SourceCompiler();
        compiler.setJavaSystemCompiler(false);
        compiler.setSource("h2.dynamic.GeneratedCalculator", "int answer() { return 42; }");

        try {
            Method method = compiler.getMethod("h2.dynamic.GeneratedCalculator");

            assertThat(method.invoke(null)).isEqualTo(42);
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }
}
