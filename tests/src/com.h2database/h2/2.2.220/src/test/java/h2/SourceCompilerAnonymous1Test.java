/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package h2;

import org.h2.util.NullBytecodeSourceCompiler;
import org.h2.util.SourceCompiler;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class SourceCompilerAnonymous1Test {
    private static final String TARGET_CLASS_NAME = "h2.generated.SystemFallbackTarget";

    @Test
    void fallsBackToSystemClassLookupWhenLegacyCompilerProducesNoBytecode() {
        SourceCompiler compiler = new NullBytecodeSourceCompiler();
        compiler.setJavaSystemCompiler(false);
        compiler.setSource(TARGET_CLASS_NAME, "Object value() { return null; }");

        assertThatThrownBy(() -> compiler.getClass(TARGET_CLASS_NAME))
                .isInstanceOf(ClassNotFoundException.class)
                .hasMessageContaining(TARGET_CLASS_NAME);
    }
}
