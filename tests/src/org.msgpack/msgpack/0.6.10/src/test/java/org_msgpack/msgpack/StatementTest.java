/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_msgpack.msgpack;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Array;
import java.util.Base64;
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.msgpack.template.builder.beans.Expression;

public class StatementTest {
    private static final String CONTEXT_ONLY_CLASS = "org_msgpack.msgpack.context.ContextOnlyClass";
    private static final byte[] CONTEXT_ONLY_CLASS_BYTES = Base64.getMimeDecoder().decode("""
            yv66vgAAADQADQoAAgADBwAEDAAFAAYBABBqYXZhL2xhbmcvT2JqZWN0AQAGPGluaXQ+
            AQADKClWBwAIAQAsb3JnX21zZ3BhY2svbXNncGFjay9jb250ZXh0L0NvbnRleHRP
            bmx5Q2xhc3MBAARDb2RlAQAPTGluZU51bWJlclRhYmxlAQAKU291cmNlRmlsZQEA
            FUNvbnRleHRPbmx5Q2xhc3MuamF2YQAhAAcAAgAAAAAAAQABAAUABgABAAkAAAAd
            AAEAAQAAAAUqtwABsQAAAAEACgAAAAYAAQAAAAIAAQALAAAAAgAM
            """);

    @Test
    void invokesArrayAccessAndArrayFactories() throws Exception {
        final String[] words = { "alpha", "beta" };
        final Object arrayElement = new Expression(words, "get", new Object[] { Integer.valueOf(1) }).getValue();
        assertThat(arrayElement).isEqualTo("beta");

        final Object emptyStringArray = new Expression(Array.class, "newInstance",
                new Object[] { String.class, Integer.valueOf(2) }).getValue();
        assertThat((String[]) emptyStringArray).containsExactly(null, null);

        final Object primitiveArray = new Expression(int.class, "newArray",
                new Object[] { Integer.valueOf(1), Integer.valueOf(2), Integer.valueOf(3) }).getValue();
        assertThat((int[]) primitiveArray).containsExactly(1, 2, 3);
    }

    @Test
    void invokesConstructorsAndMethodsSelectedByStatement() throws Exception {
        final Object constructed = new Expression(ConstructedValue.class, "new", new Object[] { "created" }).getValue();
        assertThat(constructed).isEqualTo(new ConstructedValue("created"));

        final Object factoryResult = new Expression(new NewInstanceFactory(), "newInstance", new Object[0]).getValue();
        assertThat(factoryResult).isEqualTo(new ConstructedValue("from-factory"));

        final Object staticResult = new Expression(Integer.class, "valueOf", new Object[] { "42" }).getValue();
        assertThat(staticResult).isEqualTo(Integer.valueOf(42));

        final Object classObjectResult = new Expression(String.class, "getName", new Object[0]).getValue();
        assertThat(classObjectResult).isEqualTo("java.lang.String");

        final Object iteratorResult = new Expression(new SingleValueIterator("next-value"), "next", new Object[0])
                .getValue();
        assertThat(iteratorResult).isEqualTo("next-value");

        final Object instanceResult = new Expression(new Greeter("Hello"), "greet", new Object[] { "Statement" })
                .getValue();
        assertThat(instanceResult).isEqualTo("Hello, Statement");
    }

    @Test
    void loadsClassByNameThroughStatement() throws Exception {
        final Object loadedClass = new Expression(Class.class, "forName", new Object[] { "java.lang.String" })
                .getValue();
        assertThat(loadedClass).isSameAs(String.class);
    }

    @Test
    void fallsBackToContextClassLoaderWhenClassForNameCannotUseCallerLoader() throws Exception {
        final Thread thread = Thread.currentThread();
        final ClassLoader previousContextClassLoader = thread.getContextClassLoader();
        thread.setContextClassLoader(new ContextOnlyClassLoader());
        try {
            final Object loadedClass = new Expression(Class.class, "forName", new Object[] { CONTEXT_ONLY_CLASS })
                    .getValue();
            assertThat(((Class<?>) loadedClass).getName()).isEqualTo(CONTEXT_ONLY_CLASS);
        } catch (ClassNotFoundException exception) {
            if (!isNativeImageRuntime()) {
                throw exception;
            }
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        } finally {
            thread.setContextClassLoader(previousContextClassLoader);
        }
    }

    public static final class NewInstanceFactory {
        public ConstructedValue newInstance() {
            return new ConstructedValue("from-factory");
        }
    }

    public static final class Greeter {
        private final String greeting;

        public Greeter(String greeting) {
            this.greeting = greeting;
        }

        public String greet(String name) {
            return this.greeting + ", " + name;
        }
    }

    public static final class SingleValueIterator implements Iterator<String> {
        private final String value;
        private boolean available = true;

        public SingleValueIterator(String value) {
            this.value = value;
        }

        @Override
        public boolean hasNext() {
            return this.available;
        }

        @Override
        public String next() {
            if (!this.available) {
                throw new NoSuchElementException();
            }
            this.available = false;
            return this.value;
        }
    }

    public static final class ConstructedValue {
        private final String value;

        public ConstructedValue(String value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ConstructedValue)) {
                return false;
            }
            final ConstructedValue that = (ConstructedValue) other;
            return this.value.equals(that.value);
        }

        @Override
        public int hashCode() {
            return this.value.hashCode();
        }
    }

    private static final class ContextOnlyClassLoader extends ClassLoader {
        private ContextOnlyClassLoader() {
            super(null);
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (!CONTEXT_ONLY_CLASS.equals(name)) {
                throw new ClassNotFoundException(name);
            }
            return defineClass(name, CONTEXT_ONLY_CLASS_BYTES, 0, CONTEXT_ONLY_CLASS_BYTES.length);
        }
    }

    private static boolean isNativeImageRuntime() {
        return "runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"));
    }
}
