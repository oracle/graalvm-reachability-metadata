/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sourceforge_htmlunit.htmlunit_core_js;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import net.sourceforge.htmlunit.corejs.javascript.Callable;
import net.sourceforge.htmlunit.corejs.javascript.CompilerEnvirons;
import net.sourceforge.htmlunit.corejs.javascript.Context;
import net.sourceforge.htmlunit.corejs.javascript.GeneratedClassLoader;
import net.sourceforge.htmlunit.corejs.javascript.Scriptable;
import net.sourceforge.htmlunit.corejs.javascript.ScriptableObject;
import net.sourceforge.htmlunit.corejs.javascript.SecurityController;
import net.sourceforge.htmlunit.corejs.javascript.optimizer.ClassCompiler;
import net.sourceforge.htmlunit.corejs.javascript.tools.shell.Global;
import net.sourceforge.htmlunit.corejs.javascript.tools.shell.Main;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class MainTest {
    private static final String COMPILED_SCRIPT_CLASS_NAME = "MainCompiledScript";

    @TempDir
    Path temporaryDirectory;

    @Test
    void mainInitializesJavaPolicySecurityWhenRequested() {
        if (!SecurityController.hasGlobal()) {
            SecurityController.initGlobal(new SerializableSecurityController());
        }
        String previousProperty = System.getProperty("rhino.use_java_policy_security");
        System.setProperty("rhino.use_java_policy_security", "true");
        try {
            Main.main(new String[] {"-e", "var policySecurityProbe = 'ready';"});

            assertThat(ScriptableObject.getProperty(Main.getGlobal(), "policySecurityProbe"))
                    .isEqualTo("ready");
        } finally {
            restoreSystemProperty("rhino.use_java_policy_security", previousProperty);
        }
    }

    @Test
    void processFileLoadsCompiledScriptClass() throws Exception {
        try {
            Context context = Context.enter();
            try {
                Global global = new Global(context);
                Path compiledScript = writeCompiledScriptClass();

                Main.processFile(context, global, compiledScript.toString());

                assertThat(ScriptableObject.getProperty(global, "compiledScriptProbe"))
                        .isEqualTo("executed");
            } finally {
                Context.exit();
            }
        } catch (Error error) {
            rethrowUnlessUnsupportedFeatureError(error);
        }
    }

    private Path writeCompiledScriptClass() throws IOException {
        CompilerEnvirons compilerEnvirons = new CompilerEnvirons();
        compilerEnvirons.setOptimizationLevel(0);
        ClassCompiler compiler = new ClassCompiler(compilerEnvirons);
        Object[] compiledClasses =
                compiler.compileToClassFiles(
                        "this.compiledScriptProbe = 'executed';",
                        COMPILED_SCRIPT_CLASS_NAME + ".js",
                        1,
                        COMPILED_SCRIPT_CLASS_NAME);

        Path classFile = temporaryDirectory.resolve(COMPILED_SCRIPT_CLASS_NAME + ".class");
        Files.write(classFile, (byte[]) compiledClasses[1]);
        return classFile;
    }

    private static void restoreSystemProperty(String key, String previousValue) {
        if (previousValue == null) {
            System.clearProperty(key);
            return;
        }
        System.setProperty(key, previousValue);
    }

    private static void rethrowUnlessUnsupportedFeatureError(Error error) {
        if (NativeImageSupport.isUnsupportedFeatureError(error)) {
            return;
        }
        Throwable cause = error.getCause();
        while (cause != null) {
            if (cause instanceof Error
                    && NativeImageSupport.isUnsupportedFeatureError((Error) cause)) {
                return;
            }
            cause = cause.getCause();
        }
        throw error;
    }

    private static final class SerializableSecurityController extends SecurityController
            implements java.io.Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public GeneratedClassLoader createClassLoader(
                ClassLoader parentLoader, Object securityDomain) {
            return Context.getCurrentContext().createClassLoader(parentLoader);
        }

        @Override
        public Object getDynamicSecurityDomain(Object securityDomain) {
            return securityDomain;
        }

        @Override
        public Object callWithDomain(
                Object securityDomain,
                Context context,
                Callable callable,
                Scriptable scope,
                Scriptable thisObj,
                Object[] args) {
            return callable.call(context, scope, thisObj, args);
        }
    }
}
