/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sourceforge_htmlunit.htmlunit_core_js;

import net.sourceforge.htmlunit.corejs.javascript.Context;
import net.sourceforge.htmlunit.corejs.javascript.Function;
import net.sourceforge.htmlunit.corejs.javascript.Scriptable;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CodegenTest {
    @Test
    void compileFunctionInstantiatesOptimizedNativeFunction() {
        try {
            Context cx = Context.enter();
            try {
                cx.setOptimizationLevel(0);
                Scriptable scope = cx.initStandardObjects();

                Function function =
                        cx.compileFunction(
                                scope,
                                "function add(left, right) { return left + right; }",
                                "codegen-function",
                                1,
                                null);
                Object result = function.call(cx, scope, scope, new Object[] {6, 7});

                assertThat(Context.toNumber(result)).isEqualTo(13.0);
                assertThat(cx.decompileFunction(function, 0)).contains("function add");
            } finally {
                Context.exit();
            }
        } catch (Error error) {
            rethrowUnlessUnsupportedFeatureError(error);
        }
    }

    private static void rethrowUnlessUnsupportedFeatureError(Error error) {
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
            throw error;
        }
    }
}
