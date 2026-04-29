/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_modelmapper.modelmapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.security.ProtectionDomain;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.modelmapper.internal.asm.ClassReader;
import org.modelmapper.internal.asm.ClassWriter;
import org.modelmapper.internal.asm.Type;
import org.modelmapper.internal.asm.commons.ClassRemapper;
import org.modelmapper.internal.asm.commons.Remapper;
import org.modelmapper.internal.objenesis.instantiator.android.AndroidSerializationInstantiator;

public class AndroidSerializationInstantiatorTest {
    private static final String TARGET_CLASS_NAME =
        "org.modelmapper.internal.objenesis.instantiator.android.AndroidSerializationInstantiator";
    private static final String OBJECT_STREAM_CLASS_TYPE = "java/io/ObjectStreamClass";
    private static final String SUPPORT_TYPE = Type.getInternalName(AndroidObjectStreamClassSupport.class);

    @Test
    void createsSerializableInstancesUsingAndroidSerializationConstructionRules() throws Exception {
        NonSerializableParent.constructorCalls.set(0);
        SerializableChild.constructorCalls.set(0);

        SerializableInstanceCreator creator = loadSerializableInstanceCreator();
        SerializableChild instance = creator.create();

        assertThat(instance).isNotNull();
        assertThat(NonSerializableParent.constructorCalls).hasValue(1);
        assertThat(SerializableChild.constructorCalls).hasValue(0);
        assertThat(instance.parentState).isEqualTo("initialized-by-parent");
        assertThat(instance.childState).isNull();
    }

    private static SerializableInstanceCreator loadSerializableInstanceCreator() throws Exception {
        ClassLoader classLoader = new AndroidSerializationInstantiatorClassLoader(
            AndroidSerializationInstantiatorTest.class.getClassLoader());
        Class<?> creatorType = classLoader.loadClass(SerializationCreator.class.getName());
        return (SerializableInstanceCreator) creatorType.getConstructor().newInstance();
    }

    public interface SerializableInstanceCreator {
        SerializableChild create();
    }

    public static final class SerializationCreator implements SerializableInstanceCreator {
        @Override
        public SerializableChild create() {
            AndroidSerializationInstantiator<SerializableChild> instantiator =
                new AndroidSerializationInstantiator<>(SerializableChild.class);
            return instantiator.newInstance();
        }
    }

    public static class NonSerializableParent {
        static final AtomicInteger constructorCalls = new AtomicInteger();

        String parentState;

        public NonSerializableParent() {
            constructorCalls.incrementAndGet();
            this.parentState = "initialized-by-parent";
        }
    }

    public static class SerializableChild extends NonSerializableParent implements Serializable {
        private static final long serialVersionUID = 1L;

        static final AtomicInteger constructorCalls = new AtomicInteger();

        transient String childState;

        public SerializableChild() {
            constructorCalls.incrementAndGet();
            this.childState = "initialized-by-child";
        }
    }

    public static final class AndroidObjectStreamClassSupport {
        private AndroidObjectStreamClassSupport() {
        }

        public static AndroidObjectStreamClassSupport lookupAny(Class<?> type) {
            assertThat(type).isEqualTo(SerializableChild.class);
            return new AndroidObjectStreamClassSupport();
        }

        public Object newInstance(Class<?> type) {
            if (type != SerializableChild.class) {
                throw new IllegalArgumentException("Unexpected type: " + type.getName());
            }

            SerializableChild template = new SerializableChild();
            NonSerializableParent.constructorCalls.set(0);
            SerializableChild.constructorCalls.set(0);
            return deserialize(serialize(template));
        }

        private static byte[] serialize(Serializable value) {
            try {
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
                    objectOutputStream.writeObject(value);
                }
                return outputStream.toByteArray();
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
        }

        private static Object deserialize(byte[] serializedValue) {
            try (ObjectInputStream objectInputStream =
                new ObjectInputStream(new ByteArrayInputStream(serializedValue))) {
                return objectInputStream.readObject();
            } catch (IOException | ClassNotFoundException exception) {
                throw new IllegalStateException(exception);
            }
        }
    }

    private static final class AndroidSerializationInstantiatorClassLoader extends ClassLoader {
        private AndroidSerializationInstantiatorClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> type = findLoadedClass(name);
                if (type == null && isChildFirst(name)) {
                    try {
                        type = findClass(name);
                    } catch (ClassNotFoundException ignored) {
                        type = super.loadClass(name, false);
                    }
                } else if (type == null) {
                    type = super.loadClass(name, false);
                }
                if (resolve) {
                    resolveClass(type);
                }
                return type;
            }
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            String resourceName = name.replace('.', '/') + ".class";
            try (InputStream inputStream = getParent().getResourceAsStream(resourceName)) {
                if (inputStream == null) {
                    throw new ClassNotFoundException(name);
                }
                byte[] binaryRepresentation = inputStream.readAllBytes();
                ProtectionDomain protectionDomain = null;
                if (TARGET_CLASS_NAME.equals(name)) {
                    binaryRepresentation = redirectObjectStreamClassReferences(binaryRepresentation);
                    protectionDomain = AndroidSerializationInstantiator.class.getProtectionDomain();
                }
                return defineClass(
                    name,
                    binaryRepresentation,
                    0,
                    binaryRepresentation.length,
                    protectionDomain);
            } catch (IOException exception) {
                throw new ClassNotFoundException(name, exception);
            }
        }

        private static boolean isChildFirst(String name) {
            return TARGET_CLASS_NAME.equals(name) || SerializationCreator.class.getName().equals(name);
        }

        private static byte[] redirectObjectStreamClassReferences(byte[] binaryRepresentation) {
            ClassReader reader = new ClassReader(binaryRepresentation);
            ClassWriter writer = new ClassWriter(reader, 0);
            reader.accept(new ClassRemapper(writer, new ObjectStreamClassReferenceRemapper()), 0);
            return writer.toByteArray();
        }
    }

    private static final class ObjectStreamClassReferenceRemapper extends Remapper {
        @Override
        public String map(String internalName) {
            return OBJECT_STREAM_CLASS_TYPE.equals(internalName) ? SUPPORT_TYPE : internalName;
        }
    }
}
