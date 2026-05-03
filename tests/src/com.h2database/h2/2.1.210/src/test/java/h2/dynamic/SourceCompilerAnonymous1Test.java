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

public class SourceCompilerAnonymous1Test {
    @Test
    void loadsClassThroughSourceCompilerClassLoader() throws Exception {
        SourceCompiler compiler = new SourceCompiler();
        compiler.setSource("h2.dynamic.GeneratedGreeting", "String greet() { return \"hello\"; }");

        try {
            Method method = compiler.getMethod("h2.dynamic.GeneratedGreeting");

            assertThat(method.invoke(null)).isEqualTo("hello");
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }
}
