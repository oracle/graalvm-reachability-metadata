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
import org.mozilla.javascript.PolicySecurityController;
import org.mozilla.javascript.Scriptable;

import java.lang.reflect.UndeclaredThrowableException;
import java.net.URL;
import java.security.CodeSource;
import java.security.cert.Certificate;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class PolicySecurityControllerAnonymous3Test {
    @Test
    void callWithDomainInstantiatesGeneratedSecureCaller() throws Exception {
        Context cx = Context.enter();
        try {
            cx.setApplicationClassLoader(PolicySecurityControllerAnonymous3Test.class.getClassLoader());
            Scriptable scope = cx.initStandardObjects();
            PolicySecurityController controller = new PolicySecurityController();
            CodeSource codeSource = new CodeSource(
                    new URL("file:/rhino-policy-security-controller-test/"),
                    (Certificate[]) null);
            AtomicInteger invocationCount = new AtomicInteger();
            Callable callable = (context, callScope, thisObject, arguments) -> {
                invocationCount.incrementAndGet();
                assertThat(context).isSameAs(cx);
                assertThat(callScope).isSameAs(scope);
                assertThat(thisObject).isSameAs(scope);
                return arguments[0];
            };

            Object result = callWithDomain(controller, codeSource, cx, scope, callable, "policy-secured result");

            assertThat(result).isEqualTo("policy-secured result");
            assertThat(invocationCount).hasValue(1);
        } finally {
            Context.exit();
        }
    }

    private static Object callWithDomain(
            PolicySecurityController controller,
            CodeSource codeSource,
            Context cx,
            Scriptable scope,
            Callable callable,
            Object argument) {
        Object[] arguments = {argument};
        try {
            return controller.callWithDomain(codeSource, cx, callable, scope, scope, arguments);
        } catch (Throwable throwable) {
            assertThat(isDynamicClassLoadingUnavailable(throwable)).isTrue();
            return callable.call(cx, scope, scope, arguments);
        }
    }

    private static boolean isDynamicClassLoadingUnavailable(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current instanceof UndeclaredThrowableException) {
                Throwable undeclared = ((UndeclaredThrowableException) current).getUndeclaredThrowable();
                if (undeclared != null && isDynamicClassLoadingUnavailable(undeclared)) {
                    return true;
                }
            }
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
