/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sourceforge_htmlunit.htmlunit_core_js;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.Callable;

import net.sourceforge.htmlunit.corejs.javascript.Context;
import net.sourceforge.htmlunit.corejs.javascript.JavaAdapter;
import net.sourceforge.htmlunit.corejs.javascript.Scriptable;
import net.sourceforge.htmlunit.corejs.javascript.ScriptableObject;
import net.sourceforge.htmlunit.corejs.javascript.Wrapper;
import net.sourceforge.htmlunit.corejs.javascript.serialize.ScriptableInputStream;
import net.sourceforge.htmlunit.corejs.javascript.serialize.ScriptableOutputStream;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JavaAdapterTest {
    public interface AdapterBehavior {
        String describe(int index);

        boolean active();
    }

    public abstract static class AdapterBase {
        public AdapterBase() {
        }

        public abstract String name();

        public String greet(String subject) {
            return "hello " + subject;
        }
    }

    @Test
    void createsAdapterForCustomSuperclassAndInterfaceMethods() {
        try {
            Context cx = Context.enter();
            try {
                cx.setOptimizationLevel(-1);
                Scriptable scope = cx.initStandardObjects();
                exposeClass(cx, scope, "AdapterBase", AdapterBase.class);
                exposeClass(cx, scope, "AdapterBehavior", AdapterBehavior.class);

                Object value = cx.evaluateString(scope, """
                        new JavaAdapter(
                            AdapterBase,
                            AdapterBehavior,
                            {
                                name: function() { return 'adapter'; },
                                describe: function(index) { return 'item-' + index; },
                                active: function() { return true; },
                                greet: function(subject) {
                                    return this['super$greet'](subject) + ' from adapter';
                                }
                            });
                        """, "java-adapter-custom-types", 1, null);

                Object adapter = ((Wrapper) value).unwrap();
                assertThat(adapter).isInstanceOf(AdapterBase.class);
                assertThat(adapter).isInstanceOf(AdapterBehavior.class);

                AdapterBase base = (AdapterBase) adapter;
                AdapterBehavior behavior = (AdapterBehavior) adapter;
                assertThat(base.name()).isEqualTo("adapter");
                assertThat(base.greet("duke")).isEqualTo("hello duke from adapter");
                assertThat(behavior.describe(7)).isEqualTo("item-7");
                assertThat(behavior.active()).isTrue();
            } finally {
                Context.exit();
            }
        } catch (Error error) {
            rethrowUnlessUnsupportedFeatureError(error);
        }
    }

    @Test
    void writesAndReadsSerializedAdapterObject() throws Exception {
        try {
            Context cx = Context.enter();
            try {
                cx.setOptimizationLevel(-1);
                Scriptable scope = cx.initStandardObjects();

                Object value = cx.evaluateString(scope, """
                        new JavaAdapter(
                            Packages.java.util.concurrent.Callable,
                            Packages.java.io.Serializable,
                            { call: function() { return 'serialized-call'; } });
                        """, "java-adapter-serialization", 1, null);
                Object adapter = ((Wrapper) value).unwrap();

                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                try (ScriptableOutputStream out = new ScriptableOutputStream(bytes, scope)) {
                    JavaAdapter.writeAdapterObject(adapter, out);
                }

                ByteArrayInputStream input = new ByteArrayInputStream(bytes.toByteArray());
                try (ScriptableInputStream in = new ScriptableInputStream(input, scope)) {
                    Object restored = JavaAdapter.readAdapterObject((Scriptable) value, in);

                    assertThat(restored).isInstanceOf(Callable.class);
                    assertThat(((Callable<?>) restored).call()).isEqualTo("serialized-call");
                }
            } finally {
                Context.exit();
            }
        } catch (Error error) {
            rethrowUnlessUnsupportedFeatureError(error);
        }
    }

    private static void exposeClass(
            Context cx, Scriptable scope, String name, Class<?> type) {
        Scriptable javaClass = cx.getWrapFactory().wrapJavaClass(cx, scope, type);
        ScriptableObject.putProperty(scope, name, javaClass);
    }

    private static void rethrowUnlessUnsupportedFeatureError(Error error) {
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
            throw error;
        }
    }
}
