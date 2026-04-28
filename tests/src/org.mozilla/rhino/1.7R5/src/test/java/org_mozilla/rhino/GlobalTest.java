/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mozilla.rhino;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Script;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.tools.shell.Global;

import java.io.File;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class GlobalTest {
    @TempDir
    Path tempDir;

    @Test
    void loadClassFindsAndRunsScriptClassByName() {
        Context cx = Context.enter();
        try {
            Global global = new Global(cx);
            String script = "loadClass('" + toJavaScriptString(LoadableScript.class.getName())
                    + "'); loadClassValue;";

            Object result = cx.evaluateString(
                    global,
                    script,
                    "global-loadClass-test",
                    1,
                    null);

            assertThat(Context.toString(result)).isEqualTo("loaded by Global.loadClass");
        } finally {
            Context.exit();
        }
    }

    @Test
    void serializeWritesAndDeserializeReadsShellValues() {
        Context cx = Context.enter();
        try {
            Global global = new Global(cx);
            File target = tempDir.resolve("global-serialization.bin").toFile();
            String filename = toJavaScriptString(target.getAbsolutePath());
            String script = "serialize('persisted shell value', '" + filename + "');"
                    + "String(deserialize('" + filename + "'));";

            Object result = cx.evaluateString(
                    global,
                    script,
                    "global-serialization-test",
                    1,
                    null);

            assertThat(Context.toString(result)).isEqualTo("persisted shell value");
            assertThat(target).isFile();
        } finally {
            Context.exit();
        }
    }

    private static String toJavaScriptString(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }

    public static class LoadableScript implements Script {
        public LoadableScript() {
        }

        @Override
        public Object exec(Context cx, Scriptable scope) {
            ScriptableObject.putProperty(
                    scope,
                    "loadClassValue",
                    "loaded by Global.loadClass");
            return Context.getUndefinedValue();
        }
    }
}
