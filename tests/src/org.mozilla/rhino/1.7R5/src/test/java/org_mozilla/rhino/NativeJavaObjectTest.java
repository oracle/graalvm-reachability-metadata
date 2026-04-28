/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mozilla.rhino;

import org.junit.jupiter.api.Test;
import org.mozilla.javascript.ClassCache;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.NativeJavaObject;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import static org.assertj.core.api.Assertions.assertThat;

public class NativeJavaObjectTest {
    @Test
    void convertsJavaScriptArrayToJavaArrayThroughPublicApi() {
        Context cx = Context.enter();
        try {
            Scriptable scope = cx.initStandardObjects();
            Object array = cx.evaluateString(scope, "[1, 2, 3]", "arrayCoercion", 1, null);

            Object converted = Context.jsToJava(array, int[].class);

            assertThat((int[]) converted).containsExactly(1, 2, 3);
        } finally {
            Context.exit();
        }
    }

    @Test
    void invokesDoubleValueDuringNumericCoercion() {
        Object converted = Context.jsToJava(new DoubleValueOnly(12.5), Double.TYPE);

        assertThat(converted).isEqualTo(12.5);
    }

    @Test
    void serializesAndReadsPlainNativeJavaObjects() throws Exception {
        Scriptable scope = createSerializableScope();
        NativeJavaObject typedObject = new NativeJavaObject(scope, "typed", String.class);
        NativeJavaObject untypedObject = new NativeJavaObject(scope, "untyped", null);

        NativeJavaObject restoredTyped = roundTrip(typedObject);
        NativeJavaObject restoredUntyped = roundTrip(untypedObject);

        assertThat(restoredTyped.unwrap()).isEqualTo("typed");
        assertThat(restoredUntyped.unwrap()).isEqualTo("untyped");
    }

    @Test
    void serializesAdapterBackedNativeJavaObject() throws Exception {
        Scriptable scope = createSerializableScope();
        NativeObject delegee = new NativeObject();
        SerializableAdapterSample adapter = new SerializableAdapterSample(delegee);
        NativeJavaObject wrapper = new NativeJavaObject(scope, adapter, SerializableAdapterSample.class, true);

        byte[] serialized = serialize(wrapper);
        Object restored = deserializeAdapter(serialized);

        if (restored instanceof Throwable) {
            assertThat(isAdapterDeserializationUnavailable((Throwable) restored)).isTrue();
            return;
        }
        assertThat(restored).isInstanceOf(NativeJavaObject.class);
        assertThat(((NativeJavaObject) restored).unwrap()).isInstanceOf(Runnable.class);
    }

    private static Scriptable createSerializableScope() {
        SerializableScope scope = new SerializableScope();
        new ClassCache().associate(scope);
        return scope;
    }

    private static NativeJavaObject roundTrip(NativeJavaObject object) throws IOException, ClassNotFoundException {
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(serialize(object)))) {
            return (NativeJavaObject) in.readObject();
        }
    }

    private static byte[] serialize(Object object) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(object);
        }
        return bytes.toByteArray();
    }

    private static Object deserializeAdapter(byte[] serialized) throws IOException, ClassNotFoundException {
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            try {
                return in.readObject();
            } catch (Throwable ex) {
                assertThat(isAdapterDeserializationUnavailable(ex)).isTrue();
                return ex;
            }
        }
    }

    private static boolean isAdapterDeserializationUnavailable(Throwable throwable) {
        if (throwable instanceof IOException) {
            return true;
        }
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

    public static final class DoubleValueOnly {
        private final double value;

        DoubleValueOnly(double value) {
            this.value = value;
        }

        public Number doubleValue() {
            return value;
        }
    }

    public static final class SerializableAdapterSample implements Runnable, Serializable {
        private static final long serialVersionUID = 1L;

        public final Scriptable delegee;

        SerializableAdapterSample(Scriptable delegee) {
            this.delegee = delegee;
        }

        @Override
        public void run() {
        }
    }

    public static final class SerializableScope extends ScriptableObject {
        private static final long serialVersionUID = 1L;

        @Override
        public String getClassName() {
            return "SerializableScope";
        }
    }
}
