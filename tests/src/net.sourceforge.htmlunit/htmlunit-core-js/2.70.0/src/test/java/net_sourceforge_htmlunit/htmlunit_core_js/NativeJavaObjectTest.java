/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sourceforge_htmlunit.htmlunit_core_js;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import net.sourceforge.htmlunit.corejs.javascript.Context;
import net.sourceforge.htmlunit.corejs.javascript.NativeJavaObject;
import net.sourceforge.htmlunit.corejs.javascript.Scriptable;
import net.sourceforge.htmlunit.corejs.javascript.Wrapper;
import net.sourceforge.htmlunit.corejs.javascript.serialize.ScriptableInputStream;
import net.sourceforge.htmlunit.corejs.javascript.serialize.ScriptableOutputStream;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("deprecation")
public class NativeJavaObjectTest {
    public static class NumericProbe {
        private final double value;

        public NumericProbe(double value) {
            this.value = value;
        }

        public double doubleValue() {
            return value;
        }
    }

    @Test
    void coercesNativeArrayToJavaArray() {
        Context cx = Context.enter();
        try {
            Scriptable scope = cx.initStandardObjects();
            Object nativeArray =
                    cx.evaluateString(
                            scope,
                            "['alpha', 'bravo', 'charlie'];",
                            "native-array-coercion",
                            1,
                            null);

            Object coerced = NativeJavaObject.coerceType(String[].class, nativeArray);

            assertThat((String[]) coerced).containsExactly("alpha", "bravo", "charlie");
        } finally {
            Context.exit();
        }
    }

    @Test
    void invokesDoubleValueMethodDuringNumericCoercion() {
        Object coerced = NativeJavaObject.coerceType(Double.TYPE, new NumericProbe(42.25));

        assertThat(coerced).isEqualTo(Double.valueOf(42.25));
    }

    @Test
    void serializesWrappedJavaObjectAndStaticType() throws Exception {
        Context cx = Context.enter();
        try {
            Scriptable scope = cx.initStandardObjects();
            List<String> names = new ArrayList<>();
            names.add("ada");
            names.add("grace");
            NativeJavaObject wrapper = new NativeJavaObject(scope, names, List.class);

            NativeJavaObject restored = roundTrip(wrapper, NativeJavaObject.class, scope);

            assertThat(restored.unwrap()).isEqualTo(names);
        } finally {
            Context.exit();
        }
    }

    @Test
    void serializesWrappedJavaObjectWithNullStaticType() throws Exception {
        Context cx = Context.enter();
        try {
            Scriptable scope = cx.initStandardObjects();
            NativeJavaObject wrapper = new NativeJavaObject(scope, "plain-value", null);

            NativeJavaObject restored = roundTrip(wrapper, NativeJavaObject.class, scope);

            assertThat(restored.unwrap()).isEqualTo("plain-value");
        } finally {
            Context.exit();
        }
    }

    @Test
    void serializesJavaAdapterWrapper() throws Exception {
        try {
            Context cx = Context.enter();
            try {
                cx.setOptimizationLevel(-1);
                Scriptable scope = cx.initStandardObjects();
                Object value =
                        cx.evaluateString(
                                scope,
                                """
                                new JavaAdapter(
                                    Packages.java.util.concurrent.Callable,
                                    Packages.java.io.Serializable,
                                    { call: function() { return 'adapter-call'; } });
                                """,
                                "native-java-object-adapter-serialization",
                                1,
                                null);

                NativeJavaObject restored =
                        roundTrip((NativeJavaObject) value, NativeJavaObject.class, scope);
                Object adapter = ((Wrapper) restored).unwrap();

                assertThat(adapter).isInstanceOf(Callable.class);
                assertThat(((Callable<?>) adapter).call()).isEqualTo("adapter-call");
            } finally {
                Context.exit();
            }
        } catch (Error error) {
            rethrowUnlessUnsupportedFeatureError(error);
        }
    }

    private static <T> T roundTrip(T value, Class<T> type, Scriptable scope) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ScriptableOutputStream out = new ScriptableOutputStream(bytes, scope)) {
            out.writeObject(value);
        }

        ByteArrayInputStream input = new ByteArrayInputStream(bytes.toByteArray());
        try (ScriptableInputStream in = new ScriptableInputStream(input, scope)) {
            return type.cast(in.readObject());
        }
    }

    private static void rethrowUnlessUnsupportedFeatureError(Error error) {
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
            throw error;
        }
    }
}
