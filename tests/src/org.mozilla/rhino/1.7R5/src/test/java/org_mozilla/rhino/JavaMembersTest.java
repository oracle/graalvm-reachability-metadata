/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mozilla.rhino;

import org.junit.jupiter.api.Test;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextAction;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.NativeJavaClass;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

import static org.assertj.core.api.Assertions.assertThat;

public class JavaMembersTest {
    private static final Object GLOBAL_FACTORY_LOCK = new Object();
    private static ContextFactory.GlobalSetter globalSetter;

    @Test
    void readsAndWritesPublicJavaFieldsThroughScript() {
        Context cx = Context.enter();
        try {
            Scriptable scope = cx.initStandardObjects();
            PublicFieldTarget target = new PublicFieldTarget();
            ScriptableObject.putProperty(scope, "target", Context.javaToJS(target, scope));

            Object result = cx.evaluateString(
                    scope,
                    "var before = target.count;"
                            + "target.count = 7;"
                            + "before + ':' + target.count + ':' + target.describe('value');",
                    "javaMembersFieldCoverage",
                    1,
                    null);

            assertThat(Context.toString(result)).isEqualTo("3:7:value=7");
            assertThat(target.count).isEqualTo(7);
        } finally {
            Context.exit();
        }
    }

    @Test
    void reflectsInterfaceConstructorsWhenEnhancedAccessIsEnabled() {
        Object result = withGlobalFactory(new EnhancedJavaAccessFactory(), cx -> {
            Scriptable scope = cx.initStandardObjects();
            ScriptableObject.putProperty(
                    scope,
                    "ConstructorDiscoveryTarget",
                    new NativeJavaClass(scope, ConstructorDiscoveryTarget.class));

            return cx.evaluateString(
                    scope,
                    "ConstructorDiscoveryTarget.TYPE;",
                    "javaMembersConstructorCoverage",
                    1,
                    null);
        });

        assertThat(Context.toNumber(result)).isEqualTo(42.0);
    }

    private static Object withGlobalFactory(ContextFactory factory, ContextAction action) {
        synchronized (GLOBAL_FACTORY_LOCK) {
            ContextFactory.GlobalSetter setter = getGlobalSetter();
            ContextFactory previousFactory = setter.getContextFactoryGlobal();
            setter.setContextFactoryGlobal(factory);
            try {
                return factory.call(action);
            } finally {
                setter.setContextFactoryGlobal(previousFactory);
            }
        }
    }

    private static ContextFactory.GlobalSetter getGlobalSetter() {
        if (globalSetter == null) {
            globalSetter = ContextFactory.getGlobalSetter();
        }
        return globalSetter;
    }

    private static final class EnhancedJavaAccessFactory extends ContextFactory {
        @Override
        protected boolean hasFeature(Context cx, int featureIndex) {
            if (featureIndex == Context.FEATURE_ENHANCED_JAVA_ACCESS) {
                return true;
            }
            return super.hasFeature(cx, featureIndex);
        }
    }

    public static final class PublicFieldTarget {
        public int count = 3;

        public String describe(String prefix) {
            return prefix + "=" + count;
        }
    }

    public interface ConstructorDiscoveryTarget {
        int TYPE = 42;
    }
}
