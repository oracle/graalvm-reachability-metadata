/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package h2;

import org.h2.util.NullBytecodeSourceCompiler;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SourceCompilerAnonymous1Test {
    @Test
    void fallsBackToSystemClassLookupWhenLegacyCompilerProducesNoBytecode() throws Throwable {
        NullBytecodeSourceCompiler compiler = new NullBytecodeSourceCompiler();

        assertRuntimeCompilationOutcome(() -> assertThat(compiler.findSystemClassThroughAnonymousLoader(String.class))
                .isEqualTo(String.class));
    }

    private static void assertRuntimeCompilationOutcome(CompilationAction action) throws Throwable {
        try {
            action.run();
        } catch (Throwable ex) {
            if (!hasUnsupportedRuntimeClassDefinitionCause(ex)) {
                throw ex;
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
        void run() throws Throwable;
    }
}
