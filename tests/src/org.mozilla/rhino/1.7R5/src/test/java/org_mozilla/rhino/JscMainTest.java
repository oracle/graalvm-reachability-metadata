/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mozilla.rhino;

import org.junit.jupiter.api.Test;
import org.mozilla.javascript.tools.jsc.Main;

import static org.assertj.core.api.Assertions.assertThat;

public class JscMainTest {
    @Test
    void processOptionsResolvesConfiguredSuperclassAndInterfaces() {
        Main compiler = new Main();

        String[] remainingArguments = compiler.processOptions(new String[] {
                "-extends", "java.util.ArrayList",
                "-implements", "java.io.Serializable,java.lang.Cloneable",
                "input.js"
        });

        assertThat(remainingArguments).containsExactly("input.js");
    }
}
