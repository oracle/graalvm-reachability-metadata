/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_objenesis.objenesis;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.jar.asm.ClassReader;
import net.bytebuddy.jar.asm.ClassWriter;
import net.bytebuddy.jar.asm.commons.ClassRemapper;
import net.bytebuddy.jar.asm.commons.Remapper;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.objenesis.instantiator.android.AndroidSerializationInstantiator;

public class AndroidSerializationInstantiatorTest {

    private static final String TARGET_CLASS_NAME =
        "org/objenesis/instantiator/android/AndroidSerializationInstantiator";
    private static final String SUPPORT_CLASS_NAME =
        AndroidObjectStreamClassSupport.class.getName().replace('.', '/');
    private static final AtomicBoolean transformerInstalled = new AtomicBoolean();

    @Test
    void createsSerializableInstancesUsingAndroidSerializationConstructionRules() {
        Assumptions.assumeFalse(isNativeImageRuntime());
        installObjectStreamClassRedirect();

        NonSerializableParent.constructorCalls.set(0);
        SerializableChild.constructorCalls.set(0);

        AndroidSerializationInstantiator<SerializableChild> instantiator =
            new AndroidSerializationInstantiator<>(SerializableChild.class);
        SerializableChild instance = instantiator.newInstance();

        Assertions.assertThat(instance).isNotNull();
        Assertions.assertThat(NonSerializableParent.constructorCalls).hasValue(1);
        Assertions.assertThat(SerializableChild.constructorCalls).hasValue(0);
        Assertions.assertThat(instance.parentState).isEqualTo("initialized-by-parent");
        Assertions.assertThat(instance.childState).isNull();
    }

    private static void installObjectStreamClassRedirect() {
        if (!transformerInstalled.compareAndSet(false, true)) {
            return;
        }

        Instrumentation instrumentation = ByteBuddyAgent.install();
        ClassFileTransformer transformer = new AndroidSerializationInstantiatorTransformer();
        instrumentation.addTransformer(transformer, true);
    }

    private static boolean isNativeImageRuntime() {
        return "runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"));
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
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        private static Object deserialize(byte[] serializedValue) {
            try (ObjectInputStream objectInputStream =
                new ObjectInputStream(new ByteArrayInputStream(serializedValue))) {
                return objectInputStream.readObject();
            } catch (IOException | ClassNotFoundException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    static final class AndroidSerializationInstantiatorTransformer implements ClassFileTransformer {

        @Override
        public byte[] transform(
            Module module,
            ClassLoader loader,
            String className,
            Class<?> classBeingRedefined,
            ProtectionDomain protectionDomain,
            byte[] classfileBuffer
        ) {
            if (!TARGET_CLASS_NAME.equals(className)) {
                return null;
            }

            ClassReader classReader = new ClassReader(classfileBuffer);
            ClassWriter classWriter = new ClassWriter(classReader, 0);
            ClassRemapper classRemapper = new ClassRemapper(classWriter, new Remapper() {
                @Override
                public String map(String internalName) {
                    if ("java/io/ObjectStreamClass".equals(internalName)) {
                        return SUPPORT_CLASS_NAME;
                    }
                    return internalName;
                }
            });
            classReader.accept(classRemapper, 0);
            return classWriter.toByteArray();
        }
    }
}
