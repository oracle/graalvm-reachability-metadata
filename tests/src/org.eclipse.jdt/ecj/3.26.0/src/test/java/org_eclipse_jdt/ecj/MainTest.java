/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jdt.ecj;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.eclipse.jdt.core.compiler.batch.BatchCompiler;
import org.junit.jupiter.api.Test;

public class MainTest {
    @Test
    void validatesModuleVersionOptionWithJdkModuleVersionParser() {
        final StringWriter standardOutput = new StringWriter();
        final StringWriter errorOutput = new StringWriter();

        final boolean compiled = BatchCompiler.compile(new String[] {"--module-version", "1.2.3" },
                new PrintWriter(standardOutput), new PrintWriter(errorOutput), null);

        assertThat(compiled).isTrue();
        assertThat(standardOutput.toString()).contains("Usage:");
        assertThat(errorOutput.toString()).isEmpty();
    }
}
