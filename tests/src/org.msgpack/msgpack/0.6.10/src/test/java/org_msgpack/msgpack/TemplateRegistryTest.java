/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_msgpack.msgpack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Type;
import java.util.Base64;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.msgpack.MessagePack;
import org.msgpack.MessageTypeException;
import org.msgpack.template.Template;
import org.msgpack.template.TemplateRegistry;

public class TemplateRegistryTest {
    private static final String STRING_ARRAY_CLASS_NAME = "[Ljava.lang.String;";
    private static final String CHILD_LOADED_REGISTRY_CLASS_NAME =
            "org_msgpack.msgpack.ChildLoadedTemplateRegistry";
    private static final String CHILD_LOADED_REGISTRY_CLASS_BYTES = ""
            + "yv66vgAAADQADgoAAgADBwAEDAAFAAYBACVvcmcvbXNncGFjay90ZW1wbGF0ZS9UZW1wbGF0ZVJlZ2lzdHJ5"
            + "AQAGPGluaXQ+AQAqKExvcmcvbXNncGFjay90ZW1wbGF0ZS9UZW1wbGF0ZVJlZ2lzdHJ5OylWBwAIAQAv"
            + "b3JnX21zZ3BhY2svbXNncGFjay9DaGlsZExvYWRlZFRlbXBsYXRlUmVnaXN0cnkBAAMoKVYBAARDb2Rl"
            + "AQAPTGluZU51bWJlclRhYmxlAQAKU291cmNlRmlsZQEAIENoaWxkTG9hZGVkVGVtcGxhdGVSZWdpc3Ry"
            + "eS5qYXZhADEABwACAAAAAAABAAEABQAJAAEACgAAACIAAgABAAAABioBtwABsQAAAAEACwAAAAoAAgAA"
            + "AAcABQAIAAEADAAAAAIADQ==";

    @Test
    void looksUpTemplateForGenericReferenceArrayTypeWithContextClassLoader() throws Exception {
        final TemplateRegistry registry = new TemplateRegistry(null);
        final GenericArrayType stringArrayType = genericArrayType(String.class, String.class + "[]");
        final ClassLoader previousClassLoader = Thread.currentThread().getContextClassLoader();

        Thread.currentThread().setContextClassLoader(new ArrayResolvingClassLoader(previousClassLoader));
        try {
            assertStringArrayRoundTrip(registry, stringArrayType);
        } finally {
            Thread.currentThread().setContextClassLoader(previousClassLoader);
        }
    }

    @Test
    void looksUpTemplateForGenericReferenceArrayTypeWithRegistryClassLoader() throws Exception {
        try {
            final ClassLoader previousClassLoader = Thread.currentThread().getContextClassLoader();
            final ArrayResolvingClassLoader registryClassLoader = new ArrayResolvingClassLoader(previousClassLoader);
            final TemplateRegistry registry = newChildLoadedRegistry(registryClassLoader);
            final GenericArrayType stringArrayType = genericArrayType(String.class, String.class + "[]");

            Thread.currentThread().setContextClassLoader(null);
            try {
                assertStringArrayRoundTrip(registry, stringArrayType);
            } finally {
                Thread.currentThread().setContextClassLoader(previousClassLoader);
            }
        } catch (Error error) {
            rethrowUnlessUnsupportedFeatureError(error);
        }
    }

    @Test
    void looksUpTemplateForGenericReferenceArrayTypeWithClassForNameFallback() throws Exception {
        final TemplateRegistry registry = new TemplateRegistry(null);
        final GenericArrayType stringArrayType = genericArrayType(String.class, String.class + "[]");
        final ClassLoader previousClassLoader = Thread.currentThread().getContextClassLoader();

        Thread.currentThread().setContextClassLoader(null);
        try {
            assertStringArrayRoundTrip(registry, stringArrayType);
        } finally {
            Thread.currentThread().setContextClassLoader(previousClassLoader);
        }
    }

    @Test
    void reportsMissingGenericArrayComponentTypeAfterTryingClassLoaders() {
        final TemplateRegistry registry = new TemplateRegistry(null);
        final String missingTypeName = "org_msgpack.msgpack.NoSuchMessagePackComponent";
        final GenericArrayType missingArrayType = genericArrayType(
                new NamedType("class " + missingTypeName),
                "class " + missingTypeName + "[]");
        final ClassLoader previousClassLoader = Thread.currentThread().getContextClassLoader();

        Thread.currentThread().setContextClassLoader(TemplateRegistryTest.class.getClassLoader());
        try {
            assertThatThrownBy(() -> registry.lookup(missingArrayType))
                    .isInstanceOf(MessageTypeException.class)
                    .hasMessageContaining("cannot find template of [L" + missingTypeName + ";");
        } finally {
            Thread.currentThread().setContextClassLoader(previousClassLoader);
        }
    }

    private static void assertStringArrayRoundTrip(
            final TemplateRegistry registry,
            final GenericArrayType stringArrayType) throws IOException {
        @SuppressWarnings("unchecked")
        final Template<String[]> template = (Template<String[]>) registry.lookup(stringArrayType);

        final MessagePack messagePack = new MessagePack();
        final String[] source = new String[] { "alpha", "beta", "gamma" };
        final byte[] packed = messagePack.write(source, template);

        final String[] unpacked = messagePack.read(packed, template);

        assertThat(unpacked).containsExactly("alpha", "beta", "gamma");
    }

    private static TemplateRegistry newChildLoadedRegistry(final ArrayResolvingClassLoader registryClassLoader) {
        try {
            final Class<?> registryClass = registryClassLoader.loadClass(CHILD_LOADED_REGISTRY_CLASS_NAME);
            return registryClass.asSubclass(TemplateRegistry.class).getConstructor().newInstance();
        } catch (InvocationTargetException exception) {
            final Throwable cause = exception.getCause();
            if (cause instanceof Error error) {
                throw error;
            }
            throw new AssertionError("Unable to create child-loaded TemplateRegistry", exception);
        } catch (ClassNotFoundException | IllegalAccessException | InstantiationException
                | NoSuchMethodException exception) {
            throw new AssertionError("Unable to create child-loaded TemplateRegistry", exception);
        }
    }

    private static void rethrowUnlessUnsupportedFeatureError(final Error error) {
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
            throw error;
        }
    }

    private static GenericArrayType genericArrayType(final Type componentType, final String typeName) {
        return new NamedGenericArrayType(componentType, typeName);
    }

    private static final class ArrayResolvingClassLoader extends ClassLoader {
        private ArrayResolvingClassLoader(final ClassLoader parent) {
            super(parent);
        }

        @Override
        public Class<?> loadClass(final String name) throws ClassNotFoundException {
            return loadClass(name, false);
        }

        @Override
        protected Class<?> loadClass(final String name, final boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                if (STRING_ARRAY_CLASS_NAME.equals(name)) {
                    return String[].class;
                }
                if (CHILD_LOADED_REGISTRY_CLASS_NAME.equals(name)) {
                    Class<?> loadedClass = findLoadedClass(name);
                    if (loadedClass == null) {
                        final byte[] bytes = childLoadedRegistryBytes();
                        loadedClass = defineClass(name, bytes, 0, bytes.length);
                    }
                    if (resolve) {
                        resolveClass(loadedClass);
                    }
                    return loadedClass;
                }
                return super.loadClass(name, resolve);
            }
        }

        private static byte[] childLoadedRegistryBytes() {
            return Base64.getDecoder().decode(CHILD_LOADED_REGISTRY_CLASS_BYTES);
        }
    }

    private static final class NamedGenericArrayType implements GenericArrayType {
        private final Type componentType;
        private final String typeName;

        private NamedGenericArrayType(final Type componentType, final String typeName) {
            this.componentType = componentType;
            this.typeName = typeName;
        }

        @Override
        public Type getGenericComponentType() {
            return componentType;
        }

        @Override
        public String toString() {
            return typeName;
        }
    }

    private static final class NamedType implements Type {
        private final String typeName;

        private NamedType(final String typeName) {
            this.typeName = typeName;
        }

        @Override
        public String toString() {
            return typeName;
        }
    }
}
