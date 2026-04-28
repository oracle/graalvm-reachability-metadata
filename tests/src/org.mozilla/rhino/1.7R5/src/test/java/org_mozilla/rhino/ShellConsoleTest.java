/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mozilla.rhino;

import jline.console.ConsoleReader;

import org.junit.jupiter.api.Test;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.tools.shell.ShellConsole;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ShellConsoleTest {
    @Test
    void createsJLineConsoleAndDelegatesToReader() throws IOException {
        Context cx = Context.enter();
        try {
            Scriptable scope = cx.initStandardObjects();
            scope.put("alphaValue", scope, "covered");

            ShellConsole console = ShellConsole.getConsole(scope, StandardCharsets.UTF_8);

            assertThat(console).isNotNull();
            ConsoleReader reader = ConsoleReader.lastInstance();
            assertThat(reader.isBellEnabled()).isFalse();
            assertThat(reader.getCompleter()).isNotNull();

            reader.addInput("first line");
            assertThat(console.readLine("prompt> ")).isEqualTo("first line");
            assertThat(reader.getPrompts()).containsExactly("prompt> ");

            console.print("hello");
            console.println(" world");
            console.println();
            console.flush();
            assertThat(reader.getOutput()).isEqualTo("hello world" + System.lineSeparator() + System.lineSeparator());
            assertThat(reader.isFlushed()).isTrue();

            reader.addInput("stream line");
            assertThat(readConsoleInput(console.getIn())).isEqualTo("stream line\n");

            List<String> candidates = new ArrayList<>();
            int replacementStart = reader.getCompleter().complete("alp", 3, candidates);
            assertThat(replacementStart).isZero();
            assertThat(candidates).contains("alphaValue");
        } finally {
            Context.exit();
        }
    }

    private static String readConsoleInput(InputStream in) throws IOException {
        byte[] buffer = new byte[64];
        int read = in.read(buffer);
        return new String(buffer, 0, read, StandardCharsets.UTF_8);
    }
}
