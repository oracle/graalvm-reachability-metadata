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

public class FieldAndMethodsTest {
    @Test
    void coercesFieldAndMethodsPropertyToFieldValue() {
        Context cx = Context.enter();
        try {
            Scriptable scope = cx.initStandardObjects();
            FieldAndMethodsTarget target = new FieldAndMethodsTarget();
            ScriptableObject.putProperty(scope, "target", Context.javaToJS(target, scope));

            Object result = cx.evaluateString(
                    scope,
                    "String(target.label) + ':' + target.label();",
                    "fieldAndMethodsDefaultValueCoverage",
                    1,
                    null);

            assertThat(Context.toString(result)).isEqualTo("public-field:method-value");
        } finally {
            Context.exit();
        }
    }

    public static final class FieldAndMethodsTarget {
        public String label = "public-field";

        public String label() {
            return "method-value";
        }
    }
}
