/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mozilla.rhino;

import org.junit.jupiter.api.Test;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

import static org.assertj.core.api.Assertions.assertThat;

public class NativeJavaMethodTest {
    @Test
    void expandsExplicitJavaScriptArgumentsForJavaVarargsMethod() {
        Context cx = Context.enter();
        try {
            Scriptable scope = cx.initStandardObjects();
            ScriptableObject.putProperty(scope, "target", Context.javaToJS(new VarargsTarget(), scope));

            Object result = cx.evaluateString(
                    scope,
                    "target.join('-', 'red', 'green', 'blue');",
                    "nativeJavaMethodVarargsCoverage",
                    1,
                    null);

            assertThat(Context.toString(result)).isEqualTo("red-green-blue");
        } finally {
            Context.exit();
        }
    }

    public static final class VarargsTarget {
        public String join(String delimiter, String... values) {
            return String.join(delimiter, values);
        }
    }
}
