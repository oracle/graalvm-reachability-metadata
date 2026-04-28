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
import java.lang.reflect.Method;
import java.nio.charset.Charset;
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

    @Test
    void createsJLineV1ConsoleAndDelegatesToReader() throws Exception {
        Context cx = Context.enter();
        try {
            Scriptable scope = cx.initStandardObjects();
            scope.put("betaValue", scope, "covered");

            ShellConsole console = createJLineV1Console(scope, StandardCharsets.UTF_8);

            jline.ConsoleReader reader = jline.ConsoleReader.lastInstance();
            assertThat(console).isNotNull();
            assertThat(reader.isBellEnabled()).isFalse();
            assertThat(reader.getCompletor()).isNotNull();

            reader.addInput("first v1 line");
            assertThat(console.readLine("v1> ")).isEqualTo("first v1 line");
            assertThat(reader.getPrompts()).containsExactly("v1> ");

            console.print("hello");
            console.println(" v1");
            console.println();
            console.flush();
            assertThat(reader.getOutput()).isEqualTo("hello v1" + System.lineSeparator() + System.lineSeparator());
            assertThat(reader.isFlushed()).isTrue();

            reader.addInput("v1 stream line");
            assertThat(readConsoleInput(console.getIn())).isEqualTo("v1 stream line\n");

            List<String> candidates = new ArrayList<>();
            int replacementStart = reader.getCompletor().complete("bet", 3, candidates);
            assertThat(replacementStart).isZero();
            assertThat(candidates).contains("betaValue");
        } finally {
            Context.exit();
        }
    }

    private static ShellConsole createJLineV1Console(Scriptable scope, Charset charset) throws Exception {
        Method factory = ShellConsole.class.getDeclaredMethod(
                "getJLineShellConsoleV1",
                ClassLoader.class,
                Class.class,
                Scriptable.class,
                Charset.class);
        factory.setAccessible(true);
        return (ShellConsole) factory.invoke(
                null,
                ShellConsole.class.getClassLoader(),
                jline.ConsoleReader.class,
                scope,
                charset);
    }

    private static String readConsoleInput(InputStream in) throws IOException {
        byte[] buffer = new byte[64];
        int read = in.read(buffer);
        return new String(buffer, 0, read, StandardCharsets.UTF_8);
    }
}
