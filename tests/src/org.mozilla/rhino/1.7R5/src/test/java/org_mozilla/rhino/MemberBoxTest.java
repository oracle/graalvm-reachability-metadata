/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mozilla.rhino;

import org.junit.jupiter.api.Test;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.FunctionObject;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

public class MemberBoxTest {
    @Test
    void invokesPublicStaticMethodThroughFunctionObject() throws Exception {
        Context cx = Context.enter();
        try {
            Scriptable scope = cx.initStandardObjects();
            Method method = PublicMethods.class.getMethod("join", String.class, int.class);
            FunctionObject function = new FunctionObject("join", method, scope);

            Object result = function.call(cx, scope, scope, new Object[] {"rhino", 5});

            assertThat(result).isEqualTo("rhino:5");
        } finally {
            Context.exit();
        }
    }

    @Test
    void invokesInaccessibleMethodThroughPublicInterfaceFallback() throws Exception {
        Context cx = Context.enter();
        try {
            InterfaceBackedScriptable object = new InterfaceBackedScriptable();
            Method getter = InterfaceBackedScriptable.class.getMethod("getValue");
            object.defineProperty("value", null, getter, null, ScriptableObject.EMPTY);

            Object result = object.get("value", object);

            assertThat(result).isEqualTo("from-interface");
        } finally {
            Context.exit();
        }
    }

    @Test
    void invokesInaccessibleMethodThroughPublicSuperclassFallback() throws Exception {
        Context cx = Context.enter();
        try {
            SuperclassBackedScriptable object = new SuperclassBackedScriptable();
            Method getter = SuperclassBackedScriptable.class.getMethod("getStatus");
            object.defineProperty("status", null, getter, null, ScriptableObject.EMPTY);

            Object result = object.get("status", object);

            assertThat(result).isEqualTo("from-subclass");
        } finally {
            Context.exit();
        }
    }

    @Test
    void invokesPublicConstructorThroughFunctionObject() throws Exception {
        Context cx = Context.enter();
        try {
            Scriptable scope = cx.initStandardObjects();
            Constructor<PublicConstructedScriptable> constructor = PublicConstructedScriptable.class.getConstructor(
                    String.class,
                    int.class);
            FunctionObject function = new FunctionObject("PublicConstructedScriptable", constructor, scope);

            Object result = function.call(cx, scope, null, new Object[] {"created", 7});

            assertThat(result).isInstanceOf(PublicConstructedScriptable.class);
            PublicConstructedScriptable scriptable = (PublicConstructedScriptable) result;
            assertThat(scriptable.getLabel()).isEqualTo("created");
            assertThat(scriptable.getCount()).isEqualTo(7);
        } finally {
            Context.exit();
        }
    }

    @Test
    void retriesInaccessibleConstructorAfterMakingItAccessible() throws Exception {
        Context cx = Context.enter();
        try {
            Scriptable scope = cx.initStandardObjects();
            Constructor<HiddenConstructedScriptable> constructor = HiddenConstructedScriptable.class
                    .getDeclaredConstructor(String.class);
            FunctionObject function = new FunctionObject("HiddenConstructedScriptable", constructor, scope);

            Object result = function.call(cx, scope, null, new Object[] {"hidden"});

            assertThat(result).isInstanceOf(HiddenConstructedScriptable.class);
            HiddenConstructedScriptable scriptable = (HiddenConstructedScriptable) result;
            assertThat(scriptable.getLabel()).isEqualTo("hidden");
        } finally {
            Context.exit();
        }
    }

    @Test
    void serializesAndReadsMethodBackedFunctionObject() throws Exception {
        Context cx = Context.enter();
        try {
            Scriptable scope = cx.initStandardObjects();
            Method method = PublicMethods.class.getMethod("join", String.class, int.class);
            FunctionObject function = new FunctionObject("join", method, scope);

            FunctionObject restored = roundTrip(function);
            Object result = restored.call(cx, scope, scope, new Object[] {"restored", 11});

            assertThat(result).isEqualTo("restored:11");
        } finally {
            Context.exit();
        }
    }

    @Test
    void serializesAndReadsConstructorBackedFunctionObject() throws Exception {
        Context cx = Context.enter();
        try {
            Scriptable scope = cx.initStandardObjects();
            Constructor<PublicConstructedScriptable> constructor = PublicConstructedScriptable.class.getConstructor(
                    String.class,
                    int.class);
            FunctionObject function = new FunctionObject("PublicConstructedScriptable", constructor, scope);

            FunctionObject restored = roundTrip(function);
            Object result = restored.call(cx, scope, null, new Object[] {"restored", 13});

            assertThat(result).isInstanceOf(PublicConstructedScriptable.class);
            PublicConstructedScriptable scriptable = (PublicConstructedScriptable) result;
            assertThat(scriptable.getLabel()).isEqualTo("restored");
            assertThat(scriptable.getCount()).isEqualTo(13);
        } finally {
            Context.exit();
        }
    }

    private static FunctionObject roundTrip(FunctionObject function) throws IOException, ClassNotFoundException {
        function.setParentScope(null);
        function.setPrototype(null);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(function);
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (FunctionObject) in.readObject();
        }
    }

    public interface ReadableValue {
        String getValue();
    }

    public static final class PublicMethods {
        private PublicMethods() {
        }

        public static String join(String prefix, int value) {
            return prefix + ":" + value;
        }
    }

    private static final class InterfaceBackedScriptable extends ScriptableObject implements ReadableValue {
        private static final long serialVersionUID = 1L;

        @Override
        public String getClassName() {
            return "InterfaceBackedScriptable";
        }

        @Override
        public String getValue() {
            return "from-interface";
        }
    }

    public static class PublicBaseScriptable extends ScriptableObject {
        private static final long serialVersionUID = 1L;

        @Override
        public String getClassName() {
            return "PublicBaseScriptable";
        }

        public String getStatus() {
            return "from-base";
        }
    }

    private static final class SuperclassBackedScriptable extends PublicBaseScriptable {
        private static final long serialVersionUID = 1L;

        @Override
        public String getStatus() {
            return "from-subclass";
        }
    }

    public static final class PublicConstructedScriptable extends ScriptableObject {
        private static final long serialVersionUID = 1L;

        private final String label;
        private final int count;

        public PublicConstructedScriptable(String label, int count) {
            this.label = label;
            this.count = count;
        }

        @Override
        public String getClassName() {
            return "PublicConstructedScriptable";
        }

        public String getLabel() {
            return label;
        }

        public int getCount() {
            return count;
        }
    }

    private static final class HiddenConstructedScriptable extends ScriptableObject {
        private static final long serialVersionUID = 1L;

        private final String label;

        private HiddenConstructedScriptable(String label) {
            this.label = label;
        }

        @Override
        public String getClassName() {
            return "HiddenConstructedScriptable";
        }

        String getLabel() {
            return label;
        }
    }
}
