/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sourceforge_htmlunit.htmlunit_core_js;

import java.lang.reflect.Method;
import java.security.Permission;

import net.sourceforge.htmlunit.corejs.javascript.Context;
import net.sourceforge.htmlunit.corejs.javascript.FunctionObject;
import net.sourceforge.htmlunit.corejs.javascript.Scriptable;
import net.sourceforge.htmlunit.corejs.javascript.ScriptableObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FunctionObjectTest {
    public static class ConstructedScriptable extends ScriptableObject {
        private static final long serialVersionUID = 1L;

        private String value;

        @Override
        public String getClassName() {
            return "ConstructedScriptable";
        }

        public String jsFunction_store(String newValue) {
            value = newValue;
            return "stored-" + newValue;
        }

        public String getValue() {
            return value;
        }
    }

    public static class PublicMethodDefinedScriptable extends ScriptableObject {
        private static final long serialVersionUID = 1L;

        @Override
        public String getClassName() {
            return "PublicMethodDefinedScriptable";
        }

        public String jsFunction_echo(String value) {
            return "echo-" + value;
        }
    }

    @Test
    void constructCreatesScriptableReceiverForMethodBackedFunction() throws Exception {
        Context cx = Context.enter();
        try {
            Scriptable scope = cx.initStandardObjects();
            Method method = ConstructedScriptable.class.getMethod("jsFunction_store", String.class);
            FunctionObject function = new FunctionObject("store", method, scope);

            Scriptable result = function.construct(cx, scope, new Object[] {"value"});

            assertThat(result).isInstanceOf(ConstructedScriptable.class);
            assertThat(((ConstructedScriptable) result).getValue()).isEqualTo("value");
            assertThat(result.getParentScope()).isSameAs(scope);
            assertThat(result.getPrototype()).isNotNull();
        } finally {
            Context.exit();
        }
    }

    @Test
    void defineClassFallsBackToPublicMethodScanningWhenDeclaredAccessIsDenied() throws Exception {
        Context cx = Context.enter();
        try {
            Scriptable scope = cx.initStandardObjects();

            String className =
                    withDeclaredMemberAccessDenied(
                            () ->
                                    ScriptableObject.defineClass(
                                            scope,
                                            PublicMethodDefinedScriptable.class,
                                            false,
                                            false));
            Object constructor = ScriptableObject.getProperty(scope, className);
            Object result =
                    cx.evaluateString(
                            scope,
                            "new PublicMethodDefinedScriptable().echo('ok')",
                            "function-object-public-methods",
                            1,
                            null);

            assertThat(className).isEqualTo("PublicMethodDefinedScriptable");
            assertThat(constructor).isInstanceOf(FunctionObject.class);
            assertThat(Context.toString(result)).isEqualTo("echo-ok");
        } finally {
            Context.exit();
        }
    }

    @SuppressWarnings("removal")
    private static <T> T withDeclaredMemberAccessDenied(ThrowingAction<T> action) throws Exception {
        SecurityManager previousManager = System.getSecurityManager();
        try {
            System.setSecurityManager(new DeclaredMemberDenyingSecurityManager(previousManager));
        } catch (UnsupportedOperationException unsupported) {
            return action.run();
        }

        try {
            return action.run();
        } finally {
            System.setSecurityManager(previousManager);
        }
    }

    @FunctionalInterface
    private interface ThrowingAction<T> {
        T run() throws Exception;
    }

    @SuppressWarnings("removal")
    private static final class DeclaredMemberDenyingSecurityManager extends SecurityManager {
        private final SecurityManager delegate;

        private DeclaredMemberDenyingSecurityManager(SecurityManager delegate) {
            this.delegate = delegate;
        }

        @Override
        public void checkPermission(Permission permission) {
            if (permission instanceof RuntimePermission
                    && "accessDeclaredMembers".equals(permission.getName())) {
                throw new SecurityException("Declared member access denied for fallback coverage");
            }
            if (delegate != null) {
                delegate.checkPermission(permission);
            }
        }
    }
}
