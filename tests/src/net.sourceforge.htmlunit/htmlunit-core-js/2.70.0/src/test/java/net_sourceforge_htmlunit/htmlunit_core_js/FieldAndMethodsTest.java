/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sourceforge_htmlunit.htmlunit_core_js;

import net.sourceforge.htmlunit.corejs.javascript.Context;
import net.sourceforge.htmlunit.corejs.javascript.Scriptable;
import net.sourceforge.htmlunit.corejs.javascript.ScriptableObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FieldAndMethodsTest {
    @Test
    void coercesFieldAndMethodsPropertyToTheBackingFieldValue() {
        Context cx = Context.enter();
        try {
            Scriptable scope = cx.initStandardObjects();
            FieldMethodCollision target = new FieldMethodCollision();
            ScriptableObject.putProperty(scope, "target", Context.javaToJS(target, scope));

            Object result =
                    cx.evaluateString(
                            scope,
                            """
                            var property = target.label;
                            String(property) + ':' + target.label();
                            """,
                            "field-and-methods-default-value",
                            1,
                            null);

            assertThat(Context.toString(result)).isEqualTo("field-value:method-value");
        } finally {
            Context.exit();
        }
    }

    public static class FieldMethodCollision {
        public String label = "field-value";

        public String label() {
            return "method-value";
        }
    }
}
