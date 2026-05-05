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
    private static final String JAVAX_TOOLS_CLASS_NAME = "h2.generated.SourceCompilerJavaxToolsFunction";
    private static final String LEGACY_JAVAC_CLASS_NAME = "h2.generated.SourceCompilerLegacyJavacFunction";

    @Test
    void compilesJavaSourceWithJavaxToolsAndDiscoversPublicStaticMethod() throws Exception {
        SourceCompiler compiler = new SourceCompiler();
        compiler.setJavaSystemCompiler(true);
        compiler.setSource(JAVAX_TOOLS_CLASS_NAME, """
                int twice(int value) {
                    return value * 2;
                }
                """);

        try {
            Method method = compiler.getMethod(JAVAX_TOOLS_CLASS_NAME);

            assertThat(method).isNotNull();
            assertThat(method.getName()).isEqualTo("twice");
            assertThat(method.invoke(null, 21)).isEqualTo(42);
        } catch (ClassNotFoundException exception) {
            verifyUnsupportedDynamicClassLoading(exception);
        } catch (Error error) {
            verifyUnsupportedDynamicClassLoading(error);
        }
    }

    @Test
    void compilesJavaSourceWithLegacyJavacEntryPoint() throws Exception {
        SourceCompiler compiler = new SourceCompiler();
        compiler.setJavaSystemCompiler(false);
        compiler.setSource(LEGACY_JAVAC_CLASS_NAME, """
                String greeting(String name) {
                    return "Hello, " + name;
                }
                """);

        try {
            Method method = compiler.getMethod(LEGACY_JAVAC_CLASS_NAME);

            assertThat(method).isNotNull();
            assertThat(method.getName()).isEqualTo("greeting");
            assertThat(method.invoke(null, "H2")).isEqualTo("Hello, H2");
        } catch (ClassNotFoundException exception) {
            verifyUnsupportedDynamicClassLoading(exception);
        } catch (Error error) {
            verifyUnsupportedDynamicClassLoading(error);
        }
    }

    private static void verifyUnsupportedDynamicClassLoading(Throwable throwable) throws ClassNotFoundException {
        if (throwable instanceof Error error && NativeImageSupport.isUnsupportedFeatureError(error)) {
            return;
        }
        if (throwable instanceof ClassNotFoundException exception
                && (JAVAX_TOOLS_CLASS_NAME.equals(exception.getMessage())
                        || LEGACY_JAVAC_CLASS_NAME.equals(exception.getMessage()))) {
            return;
        }
        if (throwable instanceof Error error) {
            throw error;
        }
        throw (ClassNotFoundException) throwable;
    }
}
