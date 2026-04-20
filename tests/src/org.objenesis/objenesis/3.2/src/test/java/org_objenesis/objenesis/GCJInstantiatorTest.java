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
import java.util.Set;
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
import org.objenesis.instantiator.gcj.GCJInstantiator;

public class GCJInstantiatorTest {

    private static final String TARGET_CLASS_NAME =
        "org/objenesis/instantiator/gcj/GCJInstantiator";
    private static final String TARGET_BASE_CLASS_NAME =
        "org/objenesis/instantiator/gcj/GCJInstantiatorBase";
    private static final String TARGET_DUMMY_STREAM_CLASS_NAME =
        "org/objenesis/instantiator/gcj/GCJInstantiatorBase$DummyStream";
    private static final String SUPPORT_CLASS_NAME =
        GCJObjectInputStreamSupport.class.getName().replace('.', '/');
    private static final Set<String> TARGET_CLASS_NAMES = Set.of(
        TARGET_CLASS_NAME,
        TARGET_BASE_CLASS_NAME,
        TARGET_DUMMY_STREAM_CLASS_NAME
    );
    private static final AtomicBoolean transformerInstalled = new AtomicBoolean();

    @Test
    void createsInstancesWithoutRunningConstructorsUsingGcjConstructionHook() {
        Assumptions.assumeFalse(isNativeImageRuntime());
        installObjectInputStreamRedirect();
        ConstructorBypassedType.constructorCalls.set(0);

        GCJInstantiator<ConstructorBypassedType> instantiator =
            new GCJInstantiator<>(ConstructorBypassedType.class);
        ConstructorBypassedType instance = instantiator.newInstance();

        Assertions.assertThat(instance).isNotNull();
        Assertions.assertThat(instance.state).isNull();
        Assertions.assertThat(ConstructorBypassedType.constructorCalls).hasValue(0);
    }

    private static void installObjectInputStreamRedirect() {
        if (!transformerInstalled.compareAndSet(false, true)) {
            return;
        }

        Instrumentation instrumentation = ByteBuddyAgent.install();
        ClassFileTransformer transformer = new GCJInstantiatorTransformer();
        instrumentation.addTransformer(transformer, true);
    }

    private static boolean isNativeImageRuntime() {
        return "runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"));
    }

    public static class ConstructorBypassedType implements Serializable {
        private static final long serialVersionUID = 1L;

        static final AtomicInteger constructorCalls = new AtomicInteger();

        transient String state;

        public ConstructorBypassedType() {
            constructorCalls.incrementAndGet();
            this.state = "initialized-by-constructor";
        }
    }

    public static class GCJObjectInputStreamSupport extends ObjectInputStream {

        protected GCJObjectInputStreamSupport() throws IOException {
            super();
        }

        public Object newObject(Class<?> type, Class<?> parentType) {
            if (type == ConstructorBypassedType.class && parentType == Object.class) {
                ConstructorBypassedType template = new ConstructorBypassedType();
                ConstructorBypassedType.constructorCalls.set(0);
                return deserialize(serialize(template));
            }
            if (
                type == GCJSerializationInstantiatorTest.SerializableChild.class
                    && parentType == GCJSerializationInstantiatorTest.NonSerializableParent.class
            ) {
                GCJSerializationInstantiatorTest.SerializableChild template =
                    new GCJSerializationInstantiatorTest.SerializableChild();
                GCJSerializationInstantiatorTest.NonSerializableParent.constructorCalls.set(0);
                GCJSerializationInstantiatorTest.SerializableChild.constructorCalls.set(0);
                return deserialize(serialize(template));
            }

            throw new IllegalArgumentException(
                "Unexpected instantiation request: " + type.getName() + " / " + parentType.getName()
            );
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

    static final class GCJInstantiatorTransformer implements ClassFileTransformer {

        @Override
        public byte[] transform(
            Module module,
            ClassLoader loader,
            String className,
            Class<?> classBeingRedefined,
            ProtectionDomain protectionDomain,
            byte[] classfileBuffer
        ) {
            if (!TARGET_CLASS_NAMES.contains(className)) {
                return null;
            }

            ClassReader classReader = new ClassReader(classfileBuffer);
            ClassWriter classWriter = new ClassWriter(classReader, 0);
            ClassRemapper classRemapper = new ClassRemapper(classWriter, new Remapper() {
                @Override
                public String map(String internalName) {
                    if ("java/io/ObjectInputStream".equals(internalName)) {
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
