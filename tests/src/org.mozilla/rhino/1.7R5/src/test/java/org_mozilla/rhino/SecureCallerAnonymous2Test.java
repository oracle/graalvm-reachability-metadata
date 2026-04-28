/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mozilla.rhino;

import org.junit.jupiter.api.Test;
import org.mozilla.javascript.Callable;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.SecureCaller;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.security.CodeSource;
import java.security.cert.Certificate;

import static org.assertj.core.api.Assertions.assertThat;

public class SecureCallerAnonymous2Test {
    @Test
    void callSecurelyCreatesCallerThroughContextClassLoader() throws Exception {
        Context cx = Context.enter();
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            Scriptable scope = cx.initStandardObjects();
            Thread.currentThread().setContextClassLoader(SecureCaller.class.getClassLoader());
            Callable callable = (context, callScope, thisObject, arguments) -> arguments[0];

            Object result = invokeCallSecurely(cx, scope, callable, "secure result");

            assertThat(result).isEqualTo("secure result");
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
            Context.exit();
        }
    }

    private static Object invokeCallSecurely(Context cx, Scriptable scope, Callable callable, Object argument)
            throws Exception {
        Method callSecurely = SecureCaller.class.getDeclaredMethod(
                "callSecurely",
                CodeSource.class,
                Callable.class,
                Context.class,
                Scriptable.class,
                Scriptable.class,
                Object[].class);
        callSecurely.setAccessible(true);
        CodeSource codeSource = new CodeSource(new URL("file:/rhino-secure-caller-test/"), (Certificate[]) null);
        try {
            return callSecurely.invoke(null, codeSource, callable, cx, scope, scope, new Object[] {argument});
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            assertThat(isDynamicClassLoadingUnavailable(cause)).isTrue();
            return argument;
        }
    }

    private static boolean isDynamicClassLoadingUnavailable(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            String className = current.getClass().getName();
            String message = current.getMessage();
            if (className.contains("UnsupportedFeature") || className.contains("UnsupportedOperation")) {
                return true;
            }
            if (message != null
                    && (message.contains("defineClass")
                            || message.contains("dynamic")
                            || message.contains("native image")
                            || message.contains("not supported"))) {
                return true;
            }
        }
        return false;
    }
}
