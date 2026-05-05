/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package h2;

import java.lang.reflect.Method;

import org.graalvm.internal.tck.NativeImageSupport;
import org.h2.util.SourceCompiler;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SourceCompilerTest {
    @Test
    void compilesSourceWithJavaxToolsCompilerAndDiscoversPublicStaticMethod() throws Exception {
        try {
            SourceCompiler compiler = new SourceCompiler();
            String className = "h2.generated.SourceCompilerJavaxToolsTarget";
            compiler.setJavaSystemCompiler(true);
            compiler.setSource(className, "int add(int left, int right) { return left + right; }");

            Method method = compiler.getMethod(className);

            assertThat(method).isNotNull();
            assertThat(method.getName()).isEqualTo("add");
            assertThat(method.invoke(null, 19, 23)).isEqualTo(42);
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    @Test
    void compilesSourceWithJavacMainWhenSystemCompilerIsDisabled() throws Exception {
        try {
            SourceCompiler compiler = new SourceCompiler();
            String className = "h2.generated.SourceCompilerJavacMainTarget";
            compiler.setJavaSystemCompiler(false);
            compiler.setSource(className, "String describe(int value) { return \"value=\" + value; }");

            Method method = compiler.getMethod(className);

            assertThat(method).isNotNull();
            assertThat(method.getName()).isEqualTo("describe");
            assertThat(method.invoke(null, 42)).isEqualTo("value=42");
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }
}
