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
    @Test
    void compilesSourceWithJavaxToolsCompiler() throws Exception {
        SourceCompiler compiler = new SourceCompiler();
        compiler.setJavaSystemCompiler(true);
        compiler.setSource("h2.generated.JavaxToolsGreeting", "String greet() { return \"hello from javax tools\"; }");

        assertCompiledMethodResult(compiler, "h2.generated.JavaxToolsGreeting", "hello from javax tools");
    }

    @Test
    void compilesSourceWithJavacMainFallback() throws Exception {
        SourceCompiler compiler = new SourceCompiler();
        compiler.setJavaSystemCompiler(false);
        compiler.setSource("h2.generated.JavacMainGreeting", "String greet() { return \"hello from javac main\"; }");

        assertCompiledMethodResult(compiler, "h2.generated.JavacMainGreeting", "hello from javac main");
    }

    private static void assertCompiledMethodResult(SourceCompiler compiler, String className, String expected)
            throws Exception {
        try {
            Method method = compiler.getMethod(className);
            assertThat(method).isNotNull();
            assertThat(method.invoke(null)).isEqualTo(expected);
        } catch (Throwable throwable) {
            rethrowUnlessNativeImageDynamicClassLoadingError(throwable);
        }
    }

    private static void rethrowUnlessNativeImageDynamicClassLoadingError(Throwable throwable) throws Exception {
        if (containsNativeImageDynamicClassLoadingError(throwable)) {
            return;
        }
        if (throwable instanceof Exception exception) {
            throw exception;
        }
        throw (Error) throwable;
    }

    private static boolean containsNativeImageDynamicClassLoadingError(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof Error error && NativeImageSupport.isUnsupportedFeatureError(error)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
