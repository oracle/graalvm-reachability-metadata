/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sourceforge_htmlunit.htmlunit_core_js;

import java.util.Arrays;

import net.sourceforge.htmlunit.corejs.javascript.Context;
import net.sourceforge.htmlunit.corejs.javascript.Scriptable;
import net.sourceforge.htmlunit.corejs.javascript.ScriptableObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class NativeJavaMethodTest {
    @Test
    void invokesJavaVarargsMethodWithExpandedScriptArguments() {
        Context cx = Context.enter();
        try {
            Scriptable scope = cx.initStandardObjects();
            VarargsMethodTarget target = new VarargsMethodTarget();
            ScriptableObject.putProperty(scope, "target", Context.javaToJS(target, scope));

            Object result =
                    cx.evaluateString(
                            scope,
                            "target.join('letters', 'alpha', 'bravo', 'charlie');",
                            "native-java-method-varargs",
                            1,
                            null);

            assertThat(Context.toString(result)).isEqualTo("letters:alpha,bravo,charlie");
            assertThat(target.values()).containsExactly("alpha", "bravo", "charlie");
        } finally {
            Context.exit();
        }
    }

    public static class VarargsMethodTarget {
        private String[] values = new String[0];

        public String join(String label, String... newValues) {
            values = Arrays.copyOf(newValues, newValues.length);
            return label + ":" + String.join(",", values);
        }

        private String[] values() {
            return Arrays.copyOf(values, values.length);
        }
    }
}
