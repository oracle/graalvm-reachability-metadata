/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sourceforge_htmlunit.htmlunit_core_js;

import java.security.CodeSource;
import java.security.cert.Certificate;

import net.sourceforge.htmlunit.corejs.javascript.Callable;
import net.sourceforge.htmlunit.corejs.javascript.Context;
import net.sourceforge.htmlunit.corejs.javascript.PolicySecurityController;
import net.sourceforge.htmlunit.corejs.javascript.Scriptable;
import net.sourceforge.htmlunit.corejs.javascript.ScriptableObject;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PolicySecurityControllerAnonymous3Test {
    @Test
    void callsCallableThroughPolicySecurityController() {
        try {
            Context cx = Context.enter();
            try {
                Scriptable scope = cx.initStandardObjects();
                ScriptableObject.putProperty(scope, "message", "policy");

                Object result = callWithPolicySecurityController(cx, scope);

                assertThat(result).isEqualTo("policy:domain");
            } finally {
                Context.exit();
            }
        } catch (Error error) {
            rethrowUnlessUnsupportedFeatureError(error);
        }
    }

    private static Object callWithPolicySecurityController(Context cx, Scriptable scope) {
        PolicySecurityController controller = new PolicySecurityController();
        CodeSource codeSource = new CodeSource(null, (Certificate[]) null);
        Callable callable =
                (Context context, Scriptable scriptScope, Scriptable thisObj, Object[] args) -> {
                    assertThat(context).isSameAs(cx);
                    assertThat(scriptScope).isSameAs(scope);
                    assertThat(thisObj).isSameAs(scope);
                    return ScriptableObject.getProperty(scriptScope, "message") + ":" + args[0];
                };

        return controller.callWithDomain(
                codeSource, cx, callable, scope, scope, new Object[] {"domain"});
    }

    private static void rethrowUnlessUnsupportedFeatureError(Error error) {
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
            throw error;
        }
    }
}
