/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_janino.janino;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;

import org.codehaus.janino.Compiler;
import org.junit.jupiter.api.Test;

public class CompilerTest {
    @SuppressWarnings("deprecation")
    @Test
    void deprecatedConstructorAcceptsNullLegacyPathArrays() {
        final File[] emptyPath = new File[0];

        try {
            final Compiler compiler = new Compiler(
                    emptyPath,
                    emptyPath,
                    null,
                    null,
                    null,
                    "UTF-8",
                    false,
                    false,
                    false,
                    false,
                    Compiler.DEFAULT_WARNING_HANDLE_PATTERNS,
                    false
            );

            assertThat(compiler).isNotNull();
        } catch (IllegalArgumentException exception) {
            assertThat(exception).hasMessageContaining("BOOTCLASSPATH");
        } catch (Throwable throwable) {
            JaninoNativeImageSupport.rethrowIfNotNativeImageJrtUrlAccessFailure(throwable);
        }
    }
}
