/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package h2;

import org.h2.util.SourceCompiler;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public class SourceCompilerTest {
    private static final String JAVAX_TOOLS_CLASS_NAME = "h2.generated.JavaxToolsGeneratedFunction";
    private static final String LEGACY_JAVAC_CLASS_NAME = "h2.generated.LegacyJavacGeneratedFunction";

    @Test
    void compilesSourceWithJavaxToolsCompilerAndDiscoversPublicStaticMethod() throws Exception {
        SourceCompiler compiler = new SourceCompiler();
        compiler.setSource(JAVAX_TOOLS_CLASS_NAME, """
                int add(int left, int right) {
                    return left + right;
                }
                """);

        assertRuntimeCompilationOutcome(() -> {
            Method method = compiler.getMethod(JAVAX_TOOLS_CLASS_NAME);

            assertThat(method).isNotNull();
            assertThat(method.getName()).isEqualTo("add");
            assertThat(method.getReturnType()).isEqualTo(int.class);
            assertThat(method.getParameterTypes()).containsExactly(int.class, int.class);
            assertThat(Modifier.isPublic(method.getModifiers())).isTrue();
            assertThat(Modifier.isStatic(method.getModifiers())).isTrue();
        });
    }

    @Test
    void compilesSourceWithLegacyJavacWhenSystemCompilerIsDisabled() throws Exception {
        SourceCompiler compiler = new SourceCompiler();
        compiler.setJavaSystemCompiler(false);
        compiler.setSource(LEGACY_JAVAC_CLASS_NAME, """
                String greeting() {
                    return "hello from generated source";
                }
                """);

        assertRuntimeCompilationOutcome(() -> {
            Class<?> compiledClass = compiler.getClass(LEGACY_JAVAC_CLASS_NAME);

            assertThat(compiledClass.getName()).isEqualTo(LEGACY_JAVAC_CLASS_NAME);
            assertThat(compiledClass.getClassLoader()).isNotSameAs(SourceCompilerTest.class.getClassLoader());
        });
    }

    private static void assertRuntimeCompilationOutcome(CompilationAction action) throws Exception {
        try {
            action.run();
        } catch (UnsupportedOperationException ex) {
            assertThat(ex).hasMessageContaining("Defining new classes at runtime is not supported");
        } catch (RuntimeException ex) {
            if (!hasUnsupportedRuntimeClassDefinitionCause(ex)) {
                fail("Runtime source compilation failed before class definition", ex);
            }
        }
    }

    private static boolean hasUnsupportedRuntimeClassDefinitionCause(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof UnsupportedOperationException
                    && current.getMessage() != null
                    && current.getMessage().contains("Defining new classes at runtime is not supported")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private interface CompilationAction {
        void run() throws Exception;
    }
}
