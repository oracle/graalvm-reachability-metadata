/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mozilla.rhino;

import org.junit.jupiter.api.Test;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.tools.shell.Global;

import static org.assertj.core.api.Assertions.assertThat;

public class ScriptRuntimeTest {
    @Test
    void createsShellGlobalWhenAvailable() {
        Context cx = Context.enter();
        try {
            ScriptableObject global = ScriptRuntime.getGlobal(cx);

            assertThat(global).isInstanceOf(Global.class);
            assertThat(((Global) global).isInitialized()).isTrue();
            Object result = cx.evaluateString(
                    global,
                    "typeof print + ':' + typeof loadClass",
                    "scriptRuntimeGlobalCoverage",
                    1,
                    null);
            assertThat(Context.toString(result)).isEqualTo("function:function");
        } finally {
            Context.exit();
        }
    }
}
