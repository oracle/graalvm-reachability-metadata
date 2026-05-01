/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sourceforge_htmlunit.htmlunit_core_js;

import net.sourceforge.htmlunit.corejs.javascript.BaseFunction;
import net.sourceforge.htmlunit.corejs.javascript.Context;
import net.sourceforge.htmlunit.corejs.javascript.FunctionObject;
import net.sourceforge.htmlunit.corejs.javascript.Scriptable;
import net.sourceforge.htmlunit.corejs.javascript.ScriptableObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ScriptableObjectTest {
    public static class InitBackedScriptable extends ScriptableObject {
        private static final long serialVersionUID = 1L;

        private static boolean initCalled;
        private static Context observedContext;
        private static Scriptable observedScope;
        private static boolean observedSealed;

        public static void reset() {
            initCalled = false;
            observedContext = null;
            observedScope = null;
            observedSealed = false;
        }

        public static void init(Context context, Scriptable scope, boolean sealed) {
            initCalled = true;
            observedContext = context;
            observedScope = scope;
            observedSealed = sealed;
            ScriptableObject.putProperty(scope, "initBackedMarker", "registered");
        }

        @Override
        public String getClassName() {
            return "InitBackedScriptable";
        }
    }

    public static class ConstructorBackedScriptable extends ScriptableObject {
        private static final long serialVersionUID = 1L;

        private static int constructorCalls;
        private static boolean finishInitCalled;
        private static Scriptable observedScope;
        private static FunctionObject observedConstructor;
        private static Scriptable observedPrototype;

        public ConstructorBackedScriptable() {
            constructorCalls++;
        }

        public static void reset() {
            constructorCalls = 0;
            finishInitCalled = false;
            observedScope = null;
            observedConstructor = null;
            observedPrototype = null;
        }

        public static void finishInit(
                Scriptable scope, FunctionObject constructor, Scriptable prototype) {
            finishInitCalled = true;
            observedScope = scope;
            observedConstructor = constructor;
            observedPrototype = prototype;
            ScriptableObject.putProperty(constructor, "finished", Boolean.TRUE);
        }

        public String jsFunction_describe() {
            return "constructor-backed";
        }

        @Override
        public String getClassName() {
            return "ConstructorBackedScriptable";
        }
    }

    @Test
    void defineClassInvokesStaticInitMethod() throws Exception {
        InitBackedScriptable.reset();
        Context context = Context.enter();
        try {
            Scriptable scope = context.initStandardObjects();

            String className =
                    ScriptableObject.defineClass(scope, InitBackedScriptable.class, true, false);

            assertThat(className).isNull();
            assertThat(InitBackedScriptable.initCalled).isTrue();
            assertThat(InitBackedScriptable.observedContext).isSameAs(context);
            assertThat(InitBackedScriptable.observedScope).isSameAs(scope);
            assertThat(InitBackedScriptable.observedSealed).isTrue();
            assertThat(ScriptableObject.getProperty(scope, "initBackedMarker"))
                    .isEqualTo("registered");
        } finally {
            Context.exit();
        }
    }

    @Test
    void defineClassBuildsConstructorAndRunsFinishInit() throws Exception {
        ConstructorBackedScriptable.reset();
        Context context = Context.enter();
        try {
            Scriptable scope = context.initStandardObjects();

            String className =
                    ScriptableObject.defineClass(
                            scope, ConstructorBackedScriptable.class, false, false);

            Object constructorProperty = ScriptableObject.getProperty(scope, className);
            assertThat(className).isEqualTo("ConstructorBackedScriptable");
            assertThat(constructorProperty).isInstanceOf(BaseFunction.class);
            assertThat(ConstructorBackedScriptable.constructorCalls).isEqualTo(1);
            assertThat(ConstructorBackedScriptable.finishInitCalled).isTrue();
            assertThat(ConstructorBackedScriptable.observedScope).isSameAs(scope);
            assertThat(ConstructorBackedScriptable.observedConstructor)
                    .isSameAs(constructorProperty);
            assertThat(ConstructorBackedScriptable.observedPrototype)
                    .isInstanceOf(ConstructorBackedScriptable.class);
            assertThat(ScriptableObject.getProperty(
                            (Scriptable) constructorProperty, "finished"))
                    .isEqualTo(Boolean.TRUE);
        } finally {
            Context.exit();
        }
    }
}
