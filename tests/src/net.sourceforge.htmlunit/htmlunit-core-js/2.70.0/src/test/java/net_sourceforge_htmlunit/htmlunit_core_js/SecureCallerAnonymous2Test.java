/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sourceforge_htmlunit.htmlunit_core_js;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.security.CodeSource;
import java.security.cert.Certificate;

import net.sourceforge.htmlunit.corejs.javascript.Callable;
import net.sourceforge.htmlunit.corejs.javascript.Context;
import net.sourceforge.htmlunit.corejs.javascript.Scriptable;
import net.sourceforge.htmlunit.corejs.javascript.ScriptableObject;
import net.sourceforge.htmlunit.corejs.javascript.SecureCaller;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SecureCallerAnonymous2Test {
    @Test
    void callsCallableThroughSecureCallerImplementation() throws Throwable {
        try {
            Context cx = Context.enter();
            try {
                Scriptable scope = cx.initStandardObjects();
                ScriptableObject.putProperty(scope, "token", "secure");

                Object result = callSecurely(cx, scope);

                assertThat(result).isEqualTo("secure:argument");
            } finally {
                Context.exit();
            }
        } catch (Error error) {
            rethrowUnlessUnsupportedFeatureError(error);
        }
    }

    private static Object callSecurely(Context cx, Scriptable scope) throws Throwable {
        MethodHandle callSecurely = secureCallerHandle();
        CodeSource codeSource = new CodeSource(null, (Certificate[]) null);
        Callable callable =
                (Context context, Scriptable scriptScope, Scriptable thisObj, Object[] args) -> {
                    assertThat(context).isSameAs(cx);
                    assertThat(thisObj).isSameAs(scope);
                    return ScriptableObject.getProperty(scriptScope, "token") + ":" + args[0];
                };

        return callSecurely.invoke(
                codeSource, callable, cx, scope, scope, new Object[] {"argument"});
    }

    private static MethodHandle secureCallerHandle()
            throws NoSuchMethodException, IllegalAccessException {
        MethodHandles.Lookup lookup =
                MethodHandles.privateLookupIn(SecureCaller.class, MethodHandles.lookup());
        MethodType callSecurelyType =
                MethodType.methodType(
                        Object.class,
                        CodeSource.class,
                        Callable.class,
                        Context.class,
                        Scriptable.class,
                        Scriptable.class,
                        Object[].class);
        return lookup.findStatic(SecureCaller.class, "callSecurely", callSecurelyType);
    }

    private static void rethrowUnlessUnsupportedFeatureError(Error error) {
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
            throw error;
        }
    }
}
