/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sourceforge_htmlunit.htmlunit_core_js;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import net.sourceforge.htmlunit.corejs.javascript.Context;
import net.sourceforge.htmlunit.corejs.javascript.FunctionObject;
import net.sourceforge.htmlunit.corejs.javascript.Scriptable;
import net.sourceforge.htmlunit.corejs.javascript.ScriptableObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MemberBoxTest {
    public interface ScriptableContract {
        String describe(String value);
    }

    public static class PublicMethodTarget extends ScriptableObject {
        private static final long serialVersionUID = 1L;

        @Override
        public String getClassName() {
            return "PublicMethodTarget";
        }

        public String join(String left, String right) {
            return left + ":" + right;
        }
    }

    public static class PublicBaseTarget extends ScriptableObject {
        private static final long serialVersionUID = 1L;

        @Override
        public String getClassName() {
            return "PublicBaseTarget";
        }

        public String inheritedEcho(String value) {
            return "base-" + value;
        }
    }

    public static class PublicConstructedTarget extends ScriptableObject {
        private static final long serialVersionUID = 1L;

        private final String value;

        public PublicConstructedTarget(String value) {
            this.value = value;
        }

        @Override
        public String getClassName() {
            return "PublicConstructedTarget";
        }

        public String getValue() {
            return value;
        }
    }

    private static final class InterfaceBackedTarget extends ScriptableObject
            implements ScriptableContract {
        private static final long serialVersionUID = 1L;

        @Override
        public String getClassName() {
            return "InterfaceBackedTarget";
        }

        @Override
        public String describe(String value) {
            return "interface-" + value;
        }
    }

    private static final class SuperclassBackedTarget extends PublicBaseTarget {
        private static final long serialVersionUID = 1L;

        @Override
        public String inheritedEcho(String value) {
            return "override-" + value;
        }
    }

    private static final class PrivateConstructedTarget extends ScriptableObject {
        private static final long serialVersionUID = 1L;

        private final String value;

        private PrivateConstructedTarget(String value) {
            this.value = value;
        }

        @Override
        public String getClassName() {
            return "PrivateConstructedTarget";
        }

        public String getValue() {
            return value;
        }
    }

    @Test
    void invokesPublicMethodThroughFunctionObject() throws Exception {
        Context cx = Context.enter();
        try {
            Scriptable scope = cx.initStandardObjects();
            Method method = PublicMethodTarget.class.getMethod("join", String.class, String.class);
            FunctionObject function = new FunctionObject("join", method, scope);
            PublicMethodTarget target = new PublicMethodTarget();

            Object result = function.call(cx, scope, target, new Object[] {"left", "right"});

            assertThat(result).isEqualTo("left:right");
        } finally {
            Context.exit();
        }
    }

    @Test
    void recoversInaccessibleMethodThroughPublicInterface() throws Exception {
        Context cx = Context.enter();
        try {
            Scriptable scope = cx.initStandardObjects();
            Method method = InterfaceBackedTarget.class.getDeclaredMethod("describe", String.class);
            FunctionObject function = new FunctionObject("describe", method, scope);
            InterfaceBackedTarget target = new InterfaceBackedTarget();

            Object result = function.call(cx, scope, target, new Object[] {"value"});

            assertThat(result).isEqualTo("interface-value");
        } finally {
            Context.exit();
        }
    }

    @Test
    void recoversInaccessibleMethodThroughPublicSuperclass() throws Exception {
        Context cx = Context.enter();
        try {
            Scriptable scope = cx.initStandardObjects();
            Method method =
                    SuperclassBackedTarget.class.getDeclaredMethod("inheritedEcho", String.class);
            FunctionObject function = new FunctionObject("inheritedEcho", method, scope);
            SuperclassBackedTarget target = new SuperclassBackedTarget();

            Object result = function.call(cx, scope, target, new Object[] {"value"});

            assertThat(result).isEqualTo("override-value");
        } finally {
            Context.exit();
        }
    }

    @Test
    void constructsPublicScriptableThroughFunctionObject() throws Exception {
        Context cx = Context.enter();
        try {
            Scriptable scope = cx.initStandardObjects();
            Constructor<PublicConstructedTarget> constructor =
                    PublicConstructedTarget.class.getConstructor(String.class);
            FunctionObject function =
                    new FunctionObject("PublicConstructedTarget", constructor, scope);

            PublicConstructedTarget constructed =
                    (PublicConstructedTarget)
                            function.construct(cx, scope, new Object[] {"public"});

            assertThat(constructed.getValue()).isEqualTo("public");
        } finally {
            Context.exit();
        }
    }

    @Test
    void makesInaccessibleConstructorAvailableForConstruction() throws Exception {
        Context cx = Context.enter();
        try {
            Scriptable scope = cx.initStandardObjects();
            Constructor<PrivateConstructedTarget> constructor =
                    PrivateConstructedTarget.class.getDeclaredConstructor(String.class);
            FunctionObject function =
                    new FunctionObject("PrivateConstructedTarget", constructor, scope);

            PrivateConstructedTarget constructed =
                    (PrivateConstructedTarget)
                            function.construct(cx, scope, new Object[] {"private"});

            assertThat(constructed.getValue()).isEqualTo("private");
        } finally {
            Context.exit();
        }
    }

    @Test
    void serializesAndRestoresMethodBackedMember() throws Exception {
        Context cx = Context.enter();
        try {
            Scriptable scope = cx.initStandardObjects();
            Method method = PublicMethodTarget.class.getMethod("join", String.class, String.class);
            FunctionObject function = new FunctionObject("join", method, scope);
            function.setParentScope(null);
            function.setPrototype(null);

            FunctionObject restored = roundTrip(function, FunctionObject.class);
            Object result =
                    restored.call(cx, scope, new PublicMethodTarget(), new Object[] {"a", "b"});

            assertThat(result).isEqualTo("a:b");
        } finally {
            Context.exit();
        }
    }

    @Test
    void serializesAndRestoresConstructorBackedMember() throws Exception {
        Context cx = Context.enter();
        try {
            Scriptable scope = cx.initStandardObjects();
            Constructor<PublicConstructedTarget> constructor =
                    PublicConstructedTarget.class.getConstructor(String.class);
            FunctionObject function =
                    new FunctionObject("PublicConstructedTarget", constructor, scope);
            function.setParentScope(null);
            function.setPrototype(null);

            FunctionObject restored = roundTrip(function, FunctionObject.class);
            PublicConstructedTarget constructed =
                    (PublicConstructedTarget)
                            restored.construct(cx, scope, new Object[] {"serialized"});

            assertThat(constructed.getValue()).isEqualTo("serialized");
        } finally {
            Context.exit();
        }
    }

    private static <T> T roundTrip(T value, Class<T> type) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(value);
        }

        ByteArrayInputStream byteInput = new ByteArrayInputStream(bytes.toByteArray());
        try (ObjectInputStream in = new ObjectInputStream(byteInput)) {
            return type.cast(in.readObject());
        }
    }
}
