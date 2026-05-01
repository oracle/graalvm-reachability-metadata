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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void defineClassReachesPublicMethodScanWhenPackageAccessIsDenied() throws Exception {
        Context cx = Context.enter();
        try {
            Scriptable scope = cx.initStandardObjects();

            withPackageAccessDenied(
                    () -> {
                        assertThatThrownBy(
                                        () ->
                                                ScriptableObject.defineClass(
                                                        scope,
                                                        PublicMethodDefinedScriptable.class,
                                                        false,
                                                        false))
                                .isInstanceOf(SecurityException.class)
                                .hasMessageContaining("Package access denied");
                        return null;
                    },
                    () ->
                            ScriptableObject.defineClass(
                                    scope,
                                    PublicMethodDefinedScriptable.class,
                                    false,
                                    false));
        } finally {
            Context.exit();
        }
    }

    @SuppressWarnings("removal")
    private static <T> T withDeclaredMemberAccessDenied(ThrowingAction<T> action) throws Exception {
        return withSecurityManager(new DeclaredMemberDenyingSecurityManager(null), action, action);
    }

    @SuppressWarnings("removal")
    private static <T> T withPackageAccessDenied(
            ThrowingAction<T> action, ThrowingAction<T> unsupportedAction) throws Exception {
        return withSecurityManager(
                new PackageAccessDenyingSecurityManager(
                        PublicMethodDefinedScriptable.class.getPackageName()),
                action,
                unsupportedAction);
    }

    @SuppressWarnings("removal")
    private static <T> T withSecurityManager(
            DelegatingSecurityManager securityManager,
            ThrowingAction<T> action,
            ThrowingAction<T> unsupportedAction)
            throws Exception {
        SecurityManager previousManager = System.getSecurityManager();
        securityManager.setDelegate(previousManager);
        try {
            System.setSecurityManager(securityManager);
        } catch (UnsupportedOperationException unsupported) {
            return unsupportedAction.run();
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
    private abstract static class DelegatingSecurityManager extends SecurityManager {
        private SecurityManager delegate;

        protected final void setDelegate(SecurityManager delegate) {
            this.delegate = delegate;
        }

        @Override
        public void checkPermission(Permission permission) {
            if (delegate != null) {
                delegate.checkPermission(permission);
            }
        }
    }

    @SuppressWarnings("removal")
    private static final class DeclaredMemberDenyingSecurityManager
            extends DelegatingSecurityManager {
        private DeclaredMemberDenyingSecurityManager(SecurityManager delegate) {
            setDelegate(delegate);
        }

        @Override
        public void checkPermission(Permission permission) {
            if (permission instanceof RuntimePermission
                    && "accessDeclaredMembers".equals(permission.getName())) {
                throw new SecurityException("Declared member access denied for fallback coverage");
            }
            super.checkPermission(permission);
        }
    }

    @SuppressWarnings("removal")
    private static final class PackageAccessDenyingSecurityManager
            extends DelegatingSecurityManager {
        private final String deniedPackageName;

        private PackageAccessDenyingSecurityManager(String deniedPackageName) {
            this.deniedPackageName = deniedPackageName;
        }

        @Override
        public void checkPermission(Permission permission) {
            if (permission instanceof RuntimePermission
                    && "accessDeclaredMembers".equals(permission.getName())) {
                throw new SecurityException("Declared member access denied for fallback coverage");
            }
            super.checkPermission(permission);
        }

        @Override
        public void checkPackageAccess(String packageName) {
            if (deniedPackageName.equals(packageName)) {
                throw new SecurityException("Package access denied for fallback coverage");
            }
            super.checkPackageAccess(packageName);
        }
    }
}
