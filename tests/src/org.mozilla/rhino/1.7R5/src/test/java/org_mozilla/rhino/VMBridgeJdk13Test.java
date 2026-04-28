/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mozilla.rhino;

import org.junit.jupiter.api.Test;
import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

import static org.assertj.core.api.Assertions.assertThat;

public class VMBridgeJdk13Test {
    @Test
    void adaptsJavaScriptFunctionToJavaInterfaceProxy() {
        Context cx = Context.enter();
        try {
            Scriptable scope = cx.initStandardObjects();
            ScriptableObject.putProperty(scope, "coerceGreeter", new GreeterCoercionFunction());

            Object result = cx.evaluateString(
                    scope,
                    "coerceGreeter(function(name) { return 'Hello, ' + name; });",
                    "functionInterfaceProxyCoverage",
                    1,
                    null);

            assertThat(result).isEqualTo("Hello, Rhino");
        } finally {
            Context.exit();
        }
    }

    @Test
    void adaptsJavaScriptObjectToJavaInterfaceProxy() {
        Context cx = Context.enter();
        try {
            Scriptable scope = cx.initStandardObjects();
            ScriptableObject.putProperty(scope, "coerceGreeter", new GreeterCoercionFunction());

            Object result = cx.evaluateString(
                    scope,
                    "coerceGreeter({ greet: function(name) { return 'Welcome, ' + name; } });",
                    "objectInterfaceProxyCoverage",
                    1,
                    null);

            assertThat(result).isEqualTo("Welcome, Rhino");
        } finally {
            Context.exit();
        }
    }

    public interface Greeter {
        String greet(String name);
    }

    private static final class GreeterCoercionFunction extends BaseFunction {
        private static final long serialVersionUID = 1L;

        @Override
        public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
            Greeter greeter = (Greeter) Context.jsToJava(args[0], Greeter.class);

            int hashCode = greeter.hashCode();

            assertThat(greeter.toString()).startsWith("Proxy[");
            assertThat(greeter.equals(greeter)).isTrue();
            assertThat(hashCode).isEqualTo(greeter.hashCode());
            return greeter.greet("Rhino");
        }
    }
}
