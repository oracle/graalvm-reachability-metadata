/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_extras_beanshell.bsh;

import bsh.Interpreter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class InterpreterTest {

    private static final String INVOKE_MAIN_PROPERTY = "beanshell.interpreter.invokeMain.called";

    @AfterEach
    public void clearInvokeMainProperty() {
        System.clearProperty(INVOKE_MAIN_PROPERTY);
    }

    @Test
    public void invokeMainRunsResolvedStaticMainMethod(@TempDir Path temporaryDirectory) throws Exception {
        Path script = temporaryDirectory.resolve("invoke-main.bsh");
        Files.writeString(script,
                "java.lang.System.setProperty(\"" + INVOKE_MAIN_PROPERTY + "\", \"true\");\n",
                StandardCharsets.UTF_8);

        Interpreter.invokeMain(Interpreter.class, new String[] { script.toString() });

        assertThat(System.getProperty(INVOKE_MAIN_PROPERTY)).isEqualTo("true");
    }
}
