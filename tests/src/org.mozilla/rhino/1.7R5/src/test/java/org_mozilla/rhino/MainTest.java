/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mozilla.rhino;

import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;
import org.mozilla.javascript.CompilerEnvirons;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.optimizer.ClassCompiler;
import org.mozilla.javascript.tools.shell.Global;
import org.mozilla.javascript.tools.shell.Main;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@TestMethodOrder(OrderAnnotation.class)
public class MainTest {
    @TempDir
    Path tempDirectory;

    @Test
    @Order(2)
    void mainInitializesJavaPolicySecuritySupport() {
        PrintStream originalOut = Main.getOut();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        System.setProperty("rhino.use_java_policy_security", "true");
        try (PrintStream printStream = new PrintStream(output)) {
            Main.setOut(printStream);

            Main.main(new String[] {"-e", "print('policy security initialized');"});

            assertThat(output.toString()).contains("policy security initialized");
        } finally {
            Main.setOut(originalOut);
            System.clearProperty("rhino.use_java_policy_security");
        }
    }

    @Test
    @Order(1)
    void processFileLoadsCompiledScriptClass() throws Exception {
        Path classFile = tempDirectory.resolve("CompiledShellScript.class");
        Files.write(classFile, compileScriptClass("compiledShellResult = 21 * 2;", "CompiledShellScript"));

        Context cx = Context.enter();
        try {
            Scriptable scope = new Global(cx);

            Main.processFile(cx, scope, classFile.toUri().toURL().toExternalForm());

            assertThat(Context.toNumber(scope.get("compiledShellResult", scope))).isEqualTo(42.0D);
        } finally {
            Context.exit();
        }
    }

    private static byte[] compileScriptClass(String source, String className) {
        Context cx = Context.enter();
        try {
            CompilerEnvirons compilerEnvirons = new CompilerEnvirons();
            compilerEnvirons.initFromContext(cx);
            ClassCompiler compiler = new ClassCompiler(compilerEnvirons);
            Object[] compiled = compiler.compileToClassFiles(source, className + ".js", 1, className);
            return (byte[]) compiled[1];
        } finally {
            Context.exit();
        }
    }
}
