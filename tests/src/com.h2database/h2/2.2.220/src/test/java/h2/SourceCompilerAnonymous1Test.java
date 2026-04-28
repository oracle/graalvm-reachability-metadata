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
    private static final String SOURCE_COMPILER_LOADER_ANCHOR = "h2.generated.SourceCompilerLoaderAnchor";

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
            if (!NativeImageTestSupport.hasUnsupportedRuntimeClassDefinitionCause(ex, SOURCE_COMPILER_LOADER_ANCHOR)) {
                throw ex;
            }
        }
    }

    private interface CompilationAction {
        void run() throws Throwable;
    }
}
