/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mozilla.rhino;

import org.junit.jupiter.api.Test;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.JavaAdapter;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.ObjToIntMap;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.AbstractList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class JavaAdapterTest {
    @Test
    void createsAdapterBytecodeForSuperclassAndInterfaces() {
        ObjToIntMap functionNames = new ObjToIntMap();
        functionNames.put("get", 1);
        functionNames.put("size", 0);
        functionNames.put("custom", 2);

        byte[] adapterCode = JavaAdapter.createAdapterCode(
                functionNames,
                "GeneratedListAdapterForCoverage",
                AbstractList.class,
                new Class<?>[] {List.class, Serializable.class},
                null);

        assertThat(adapterCode).isNotEmpty();
    }

    @Test
    void returnsSelfFieldFromAdapterInstance() throws Exception {
        NativeObject self = new NativeObject();
        JavaAdapterSelfHolder holder = new JavaAdapterSelfHolder(self);

        Object result = JavaAdapter.getAdapterSelf(JavaAdapterSelfHolder.class, holder);

        assertThat(result).isSameAs(self);
    }

    @Test
    void writesAdapterDescriptorAndDelegate() throws Exception {
        NativeObject delegee = new NativeObject();
        delegee.defineProperty("marker", "stored", ScriptableObject.EMPTY);
        JavaAdapterSerializableSample adapter = new JavaAdapterSerializableSample(delegee);

        byte[] serialized = writeAdapterObject(adapter);

        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            assertThat(in.readObject()).isEqualTo(Object.class.getName());
            assertThat((String[]) in.readObject()).contains(Serializable.class.getName(), Runnable.class.getName());
            assertThat(in.readObject()).isInstanceOf(Scriptable.class);
        }
    }

    @Test
    void readsSerializedAdapterDescriptor() throws Exception {
        Context cx = Context.enter();
        try {
            Scriptable scope = cx.initStandardObjects();
            NativeObject delegee = new NativeObject();
            byte[] serialized = writeAdapterDescriptor(Object.class, new Class<?>[] {Serializable.class}, delegee);

            Object adapter = readAdapterObject(scope, serialized);

            assertThat(adapter).isNotNull();
        } finally {
            Context.exit();
        }
    }

    @Test
    void createsRunnableAdapterThroughJavaScriptConstructor() {
        Context cx = Context.enter();
        try {
            Scriptable scope = cx.initStandardObjects();

            Object adapter = evaluateAdapterConstruction(cx, scope);

            assertThat(adapter).isNotNull();
            if (adapter instanceof Throwable) {
                assertThat(isDynamicClassLoadingUnavailable((Throwable) adapter)).isTrue();
                return;
            }
            assertThat(Context.jsToJava(adapter, Runnable.class)).isInstanceOf(Runnable.class);
        } finally {
            Context.exit();
        }
    }

    private static byte[] writeAdapterObject(Object adapter) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            JavaAdapter.writeAdapterObject(adapter, out);
        }
        return bytes.toByteArray();
    }

    private static byte[] writeAdapterDescriptor(Class<?> superClass, Class<?>[] interfaces, Scriptable delegee)
            throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(superClass.getName());
            out.writeObject(Arrays.stream(interfaces).map(Class::getName).toArray(String[]::new));
            out.writeObject(delegee);
        }
        return bytes.toByteArray();
    }

    private static Object readAdapterObject(Scriptable scope, byte[] serialized) throws Exception {
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            try {
                return JavaAdapter.readAdapterObject(scope, in);
            } catch (Throwable ex) {
                assertThat(isDynamicClassLoadingUnavailable(ex)).isTrue();
                return ex;
            }
        }
    }

    private static Object evaluateAdapterConstruction(Context cx, Scriptable scope) {
        try {
            return cx.evaluateString(
                    scope,
                    "new JavaAdapter(java.lang.Runnable, { run: function() { return 'ran'; } });",
                    "javaAdapterCoverage",
                    1,
                    null);
        } catch (Throwable ex) {
            assertThat(isDynamicClassLoadingUnavailable(ex)).isTrue();
            return ex;
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

    public static final class JavaAdapterSelfHolder {
        public final Scriptable self;

        JavaAdapterSelfHolder(Scriptable self) {
            this.self = self;
        }
    }

    public static final class JavaAdapterSerializableSample implements Runnable, Serializable {
        private static final long serialVersionUID = 1L;

        public final Scriptable delegee;

        JavaAdapterSerializableSample(Scriptable delegee) {
            this.delegee = delegee;
        }

        @Override
        public void run() {
        }
    }
}
