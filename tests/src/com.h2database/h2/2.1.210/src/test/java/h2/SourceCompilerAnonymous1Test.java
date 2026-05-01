/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package h2;

import org.graalvm.internal.tck.NativeImageSupport;
import org.h2.util.NullBytecodeSourceCompiler;
import org.h2.util.SourceCompiler;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

public class SourceCompilerAnonymous1Test {
    private static final String MISSING_SYSTEM_CLASS = "h2.generated.MissingSystemClass";

    @Test
    void fallsBackToSystemClassLookupWhenLegacyCompilerProducesNoBytecode() throws Exception {
        assertDynamicClassLoading(() -> {
            SourceCompiler compiler = new NullBytecodeSourceCompiler();
            compiler.setJavaSystemCompiler(false);
            compiler.setSource(MISSING_SYSTEM_CLASS, "String value() { return \"unreachable\"; }");

            Throwable thrown = catchThrowable(() -> compiler.getClass(MISSING_SYSTEM_CLASS));

            assertThat(thrown)
                    .isInstanceOf(ClassNotFoundException.class)
                    .hasMessage(MISSING_SYSTEM_CLASS);
        });
    }

    private static void assertDynamicClassLoading(DynamicClassLoadingAssertion assertion) throws Exception {
        try {
            assertion.run();
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private interface DynamicClassLoadingAssertion {
        void run() throws Exception;
    }
}
