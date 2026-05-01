/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sourceforge_htmlunit.htmlunit_core_js;

import java.security.Permission;
import java.util.List;

import net.sourceforge.htmlunit.corejs.javascript.Context;
import net.sourceforge.htmlunit.corejs.javascript.ContextAction;
import net.sourceforge.htmlunit.corejs.javascript.ContextFactory;
import net.sourceforge.htmlunit.corejs.javascript.NativeJavaClass;
import net.sourceforge.htmlunit.corejs.javascript.Scriptable;
import net.sourceforge.htmlunit.corejs.javascript.ScriptableObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JavaMembersTest {
    private static final Object CONTEXT_FACTORY_LOCK = new Object();

    public static class FieldBackedJavaObject {
        public String message = "initial";
        public int count = 1;

        public String describe() {
            return message + ":" + count;
        }
    }

    public interface EnhancedAccessContract {
        String NAME = "enhanced-access";
    }

    @Test
    void readsAndWritesPublicJavaFieldsFromScript() {
        Context cx = Context.enter();
        try {
            Scriptable scope = cx.initStandardObjects();
            FieldBackedJavaObject target = new FieldBackedJavaObject();
            ScriptableObject.putProperty(scope, "target", Context.javaToJS(target, scope));

            Object result =
                    cx.evaluateString(
                            scope,
                            """
                            target.message = 'updated';
                            target.count = 7;
                            target.message + ':' + target.count + ':' + target.describe();
                            """,
                            "java-members-public-fields",
                            1,
                            null);

            assertThat(target.message).isEqualTo("updated");
            assertThat(target.count).isEqualTo(7);
            assertThat(Context.toString(result)).isEqualTo("updated:7:updated:7");
        } finally {
            Context.exit();
        }
    }

    @Test
    void reflectsDeclaredConstructorsWhenEnhancedJavaAccessIsEnabled() {
        NativeJavaClass wrapper =
                withEnhancedJavaAccess(
                        cx -> {
                            Scriptable scope = cx.initStandardObjects();
                            return new NativeJavaClass(scope, EnhancedAccessContract.class);
                        });

        assertThat(wrapper.getClassObject()).isEqualTo(EnhancedAccessContract.class);
        assertThat(wrapper.has("NAME", wrapper)).isTrue();
    }

    @Test
    void fallsBackToPublicMethodsWhenDeclaredMethodAccessIsDenied() {
        Context cx = Context.enter();
        try {
            Scriptable scope = cx.initStandardObjects();
            withDeclaredMemberAccessDenied(
                    () -> {
                        NativeJavaClass wrapper = new NativeJavaClass(scope, List.class, true);

                        assertThat(wrapper.has("of", wrapper)).isTrue();
                    });
        } finally {
            Context.exit();
        }
    }

    private static <T> T withEnhancedJavaAccess(ContextAction<T> action) {
        synchronized (CONTEXT_FACTORY_LOCK) {
            ContextFactory.GlobalSetter setter = GlobalContextFactorySetter.SETTER;
            ContextFactory previousFactory = setter.getContextFactoryGlobal();
            setter.setContextFactoryGlobal(new EnhancedJavaAccessContextFactory());
            try {
                return ContextFactory.getGlobal().call(action);
            } finally {
                setter.setContextFactoryGlobal(previousFactory);
            }
        }
    }

    @SuppressWarnings("removal")
    private static void withDeclaredMemberAccessDenied(Runnable action) {
        SecurityManager previousManager = System.getSecurityManager();
        try {
            System.setSecurityManager(new DeclaredMemberDenyingSecurityManager(previousManager));
        } catch (UnsupportedOperationException unsupported) {
            action.run();
            return;
        }

        try {
            action.run();
        } finally {
            System.setSecurityManager(previousManager);
        }
    }

    private static final class EnhancedJavaAccessContextFactory extends ContextFactory {
        @Override
        protected boolean hasFeature(Context cx, int featureIndex) {
            if (featureIndex == Context.FEATURE_ENHANCED_JAVA_ACCESS) {
                return true;
            }
            return super.hasFeature(cx, featureIndex);
        }
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

    private static final class GlobalContextFactorySetter {
        private static final ContextFactory.GlobalSetter SETTER = ContextFactory.getGlobalSetter();
    }
}
