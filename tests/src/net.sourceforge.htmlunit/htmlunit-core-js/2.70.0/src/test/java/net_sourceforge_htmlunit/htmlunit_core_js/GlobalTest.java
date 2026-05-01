/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sourceforge_htmlunit.htmlunit_core_js;

import java.nio.file.Path;

import net.sourceforge.htmlunit.corejs.javascript.Context;
import net.sourceforge.htmlunit.corejs.javascript.Script;
import net.sourceforge.htmlunit.corejs.javascript.Scriptable;
import net.sourceforge.htmlunit.corejs.javascript.ScriptableObject;
import net.sourceforge.htmlunit.corejs.javascript.tools.shell.Global;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class GlobalTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void loadClassResolvesAndExecutesScriptClassByName() {
        try {
            Context context = Context.enter();
            try {
                Global global = new Global(context);

                String source = "loadClass('" + RecordedScript.class.getName() + "')";
                context.evaluateString(global, source, "load-class-test", 1, null);

                assertThat(ScriptableObject.getProperty(global, "recordedScriptExecuted"))
                        .isEqualTo(Boolean.TRUE);
            } finally {
                Context.exit();
            }
        } catch (Error error) {
            rethrowUnlessUnsupportedFeatureError(error);
        }
    }

    @Test
    void serializeAndDeserializeRoundTripObjectThroughShellGlobal() throws Exception {
        Context context = Context.enter();
        try {
            Global global = new Global(context);
            Path serializedFile = temporaryDirectory.resolve("global-payload.ser");

            Global.serialize(
                    context,
                    global,
                    new Object[] {"serialized payload", serializedFile.toString()},
                    null);
            Object restored =
                    Global.deserialize(
                            context, global, new Object[] {serializedFile.toString()}, null);

            assertThat(Context.toString(restored)).isEqualTo("serialized payload");
        } finally {
            Context.exit();
        }
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

    public static class RecordedScript implements Script {
        private static final long serialVersionUID = 1L;

        @Override
        public Object exec(Context context, Scriptable scope) {
            ScriptableObject.putProperty(scope, "recordedScriptExecuted", Boolean.TRUE);
            return null;
        }
    }
}
