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

public class SourceCompilerTest {
    private static final String JAVAX_TOOLS_CLASS = "h2.generated.JavaxToolsCompiledFunction";
    private static final String LEGACY_JAVAC_CLASS = "h2.generated.LegacyJavacCompiledFunction";

    @Test
    void compilesWithJavaxToolsCompilerAndDiscoversStaticMethod() throws Exception {
        SourceCompiler compiler = new SourceCompiler();
        compiler.setJavaSystemCompiler(true);
        compiler.setSource(JAVAX_TOOLS_CLASS, "int add(int left, int right) { return left + right; }");

        assertDynamicCompilation(() -> {
            Method method = compiler.getMethod(JAVAX_TOOLS_CLASS);

            assertThat(method).isNotNull();
            assertThat(method.getName()).isEqualTo("add");
            assertThat(method.invoke(null, 19, 23)).isEqualTo(42);
        });
    }

    @Test
    void compilesWithLegacySunJavacAndDiscoversStaticMethod() throws Exception {
        SourceCompiler compiler = new SourceCompiler();
        compiler.setJavaSystemCompiler(false);
        compiler.setSource(LEGACY_JAVAC_CLASS, "String echo(String value) { return \"legacy-\" + value; }");

        assertDynamicCompilation(() -> {
            Method method = compiler.getMethod(LEGACY_JAVAC_CLASS);

            assertThat(method).isNotNull();
            assertThat(method.getName()).isEqualTo("echo");
            assertThat(method.invoke(null, "javac")).isEqualTo("legacy-javac");
        });
    }

    private static void assertDynamicCompilation(DynamicCompilationAssertion assertion) throws Exception {
        try {
            assertion.run();
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private interface DynamicCompilationAssertion {
        void run() throws Exception;
    }
}
