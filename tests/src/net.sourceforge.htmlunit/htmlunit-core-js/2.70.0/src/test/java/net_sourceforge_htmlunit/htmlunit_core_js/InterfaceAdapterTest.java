/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sourceforge_htmlunit.htmlunit_core_js;

import net.sourceforge.htmlunit.corejs.javascript.Context;
import net.sourceforge.htmlunit.corejs.javascript.EvaluatorException;
import net.sourceforge.htmlunit.corejs.javascript.Scriptable;
import net.sourceforge.htmlunit.corejs.javascript.ScriptableObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class InterfaceAdapterTest {
    public interface MultipleOperations {
        String describe(String value);

        int length(String value);
    }

    public static class OperationHarness {
        public String use(MultipleOperations operations) {
            return operations.describe("rhino") + operations.length("rhino");
        }
    }

    @Test
    void rejectsFunctionConversionForInterfaceWithDifferentMethodNames() {
        Context cx = Context.enter();
        try {
            Scriptable scope = cx.initStandardObjects();
            OperationHarness harness = new OperationHarness();
            ScriptableObject.putProperty(scope, "harness", Context.javaToJS(harness, scope));

            assertThatThrownBy(
                            () ->
                                    cx.evaluateString(
                                            scope,
                                            """
                                            harness.use(function(value) {
                                                return value;
                                            });
                                            """,
                                            "interface-adapter-rejection",
                                            1,
                                            null))
                    .isInstanceOf(EvaluatorException.class)
                    .hasMessageContaining("different names");
        } finally {
            Context.exit();
        }
    }
}
