/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mozilla.rhino;

import org.junit.jupiter.api.Test;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.Scriptable;

import static org.assertj.core.api.Assertions.assertThat;

public class CodegenTest {
    @Test
    void compileFunctionCreatesOptimizedCallableFunction() {
        Context cx = Context.enter();
        try {
            cx.setOptimizationLevel(1);
            Scriptable scope = cx.initStandardObjects();
            Function function = cx.compileFunction(
                    scope,
                    "function add(left, right) { return left + right; }",
                    "codegen-compile-function-test",
                    1,
                    null);

            Object result = function.call(cx, scope, scope, new Object[] {6, 7});

            assertThat(Context.toNumber(result)).isEqualTo(13.0D);
        } finally {
            Context.exit();
        }
    }
}
