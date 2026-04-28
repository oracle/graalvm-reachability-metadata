/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mozilla.rhino;

import org.junit.jupiter.api.Test;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.NativeJavaClass;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

public class NativeJavaClassTest {
    @Test
    void constructsJavaVarargsBySpreadingScriptArguments() {
        Context cx = Context.enter();
        try {
            Scriptable scope = cx.initStandardObjects();
            ScriptableObject.putProperty(scope, "VarargTarget", new NativeJavaClass(scope, VarargTarget.class));

            Object result = cx.evaluateString(
                    scope,
                    "var target = new VarargTarget('item', 2, 3, 5); target.summary();",
                    "nativeJavaClassVarargs",
                    1,
                    null);

            assertThat(Context.toString(result)).isEqualTo("item=[2, 3, 5]");
        } finally {
            Context.exit();
        }
    }

    public static final class VarargTarget {
        private final String label;
        private final int[] values;

        public VarargTarget(String label, int... values) {
            this.label = label;
            this.values = values.clone();
        }

        public String summary() {
            return label + "=" + Arrays.toString(values);
        }
    }
}
