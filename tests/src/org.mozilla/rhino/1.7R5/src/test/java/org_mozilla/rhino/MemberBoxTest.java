/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mozilla.rhino;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.FunctionObject;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class MemberBoxTest {
    @Test
    void invokesPublicMethodAndRecoversAccessibleMethods() throws Exception {
        Context context = Context.enter();
        try {
            Scriptable scope = context.initStandardObjects();

            FunctionObject publicFunction = functionForMethod(
                    scope,
                    "staticEcho",
                    StaticFunctions.class.getMethod("staticEcho", String.class));
            assertEquals("static:plain", publicFunction.call(context, scope, scope, new Object[] {"plain"}));

            Method interfaceMethod = InterfaceBackedScriptable.class.getDeclaredMethod("label", String.class);
            FunctionObject interfaceFunction = functionForMethod(scope, "label", interfaceMethod);
            InterfaceBackedScriptable interfaceTarget = new InterfaceBackedScriptable();
            assertEquals("interface:value", interfaceFunction.call(
                    context,
                    scope,
                    interfaceTarget,
                    new Object[] {"value"}));

            Method superclassMethod = SuperclassBackedScriptable.class.getDeclaredMethod("describe", String.class);
            FunctionObject superclassFunction = functionForMethod(scope, "describe", superclassMethod);
            SuperclassBackedScriptable superclassTarget = new SuperclassBackedScriptable();
            assertEquals("sub:value", superclassFunction.call(
                    context,
                    scope,
                    superclassTarget,
                    new Object[] {"value"}));
        } finally {
            Context.exit();
        }
    }

    @Test
    void constructsPublicAndPrivateScriptableObjects() throws Exception {
        Context context = Context.enter();
        try {
            Scriptable scope = context.initStandardObjects();

            Constructor<PublicConstructedScriptable> publicConstructor =
                    PublicConstructedScriptable.class.getConstructor(String.class);
            FunctionObject publicFunction = functionForConstructor(
                    scope,
                    "PublicConstructedScriptable",
                    publicConstructor);
            Object publicResult = publicFunction.call(context, scope, null, new Object[] {"public"});
            PublicConstructedScriptable publicScriptable = assertInstanceOf(
                    PublicConstructedScriptable.class,
                    publicResult);
            assertEquals("public", publicScriptable.value());

            Constructor<PrivateConstructedScriptable> privateConstructor =
                    PrivateConstructedScriptable.class.getDeclaredConstructor(String.class);
            FunctionObject privateFunction = functionForConstructor(
                    scope,
                    "PrivateConstructedScriptable",
                    privateConstructor);
            Object privateResult = privateFunction.call(context, scope, null, new Object[] {"private"});
            PrivateConstructedScriptable privateScriptable = assertInstanceOf(
                    PrivateConstructedScriptable.class,
                    privateResult);
            assertEquals("private", privateScriptable.value());
        } finally {
            Context.exit();
        }
    }

    @Test
    void serializesFunctionObjectsBackedByMethodsAndConstructors() throws Exception {
        Context context = Context.enter();
        try {
            Scriptable scope = context.initStandardObjects();

            FunctionObject methodFunction = functionForMethod(
                    scope,
                    "staticEcho",
                    StaticFunctions.class.getMethod("staticEcho", String.class));
            FunctionObject restoredMethodFunction = roundTrip(methodFunction);
            assertEquals("static:restored", restoredMethodFunction.call(
                    context,
                    scope,
                    scope,
                    new Object[] {"restored"}));

            FunctionObject constructorFunction = functionForConstructor(
                    scope,
                    "PublicConstructedScriptable",
                    PublicConstructedScriptable.class.getConstructor(String.class));
            FunctionObject restoredConstructorFunction = roundTrip(constructorFunction);
            Object restoredResult = restoredConstructorFunction.call(context, scope, null, new Object[] {"restored"});
            PublicConstructedScriptable restoredScriptable = assertInstanceOf(
                    PublicConstructedScriptable.class,
                    restoredResult);
            assertEquals("restored", restoredScriptable.value());
        } finally {
            Context.exit();
        }
    }

    private static FunctionObject functionForMethod(Scriptable scope, String name, Method method) {
        return detachFromScope(new FunctionObject(name, method, scope));
    }

    private static FunctionObject functionForConstructor(Scriptable scope, String name, Constructor<?> constructor) {
        return detachFromScope(new FunctionObject(name, constructor, scope));
    }

    private static FunctionObject detachFromScope(FunctionObject function) {
        function.setParentScope(null);
        function.setPrototype(null);
        return function;
    }

    private static FunctionObject roundTrip(FunctionObject function) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(function);
        }
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (FunctionObject) input.readObject();
        }
    }

    public static final class StaticFunctions {
        private StaticFunctions() {
        }

        public static String staticEcho(String value) {
            return "static:" + value;
        }
    }

    public interface AccessibleContract {
        String label(String value);
    }

    private static final class InterfaceBackedScriptable extends ScriptableObject implements AccessibleContract {
        private static final long serialVersionUID = 1L;

        @Override
        public String getClassName() {
            return "InterfaceBackedScriptable";
        }

        @Override
        public String label(String value) {
            return "interface:" + value;
        }
    }

    public static class PublicScriptableBase extends ScriptableObject {
        private static final long serialVersionUID = 1L;

        @Override
        public String getClassName() {
            return "PublicScriptableBase";
        }

        public String describe(String value) {
            return "base:" + value;
        }
    }

    private static final class SuperclassBackedScriptable extends PublicScriptableBase {
        private static final long serialVersionUID = 1L;

        @Override
        public String getClassName() {
            return "SuperclassBackedScriptable";
        }

        @Override
        public String describe(String value) {
            return "sub:" + value;
        }
    }

    public static class PublicConstructedScriptable extends ScriptableObject {
        private static final long serialVersionUID = 1L;

        private final String value;

        public PublicConstructedScriptable(String value) {
            this.value = value;
        }

        @Override
        public String getClassName() {
            return "PublicConstructedScriptable";
        }

        public String value() {
            return value;
        }
    }

    private static final class PrivateConstructedScriptable extends ScriptableObject {
        private static final long serialVersionUID = 1L;

        private final String value;

        private PrivateConstructedScriptable(String value) {
            this.value = value;
        }

        @Override
        public String getClassName() {
            return "PrivateConstructedScriptable";
        }

        String value() {
            return value;
        }
    }
}
