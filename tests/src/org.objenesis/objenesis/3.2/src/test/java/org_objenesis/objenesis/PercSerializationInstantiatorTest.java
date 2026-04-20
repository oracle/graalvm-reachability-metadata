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
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.ClassWriter;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import net.bytebuddy.jar.asm.commons.ClassRemapper;
import net.bytebuddy.jar.asm.commons.Remapper;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.objenesis.instantiator.perc.PercSerializationInstantiator;

public class PercSerializationInstantiatorTest {

    private static final String TARGET_CLASS_NAME =
        "org/objenesis/instantiator/perc/PercSerializationInstantiator";
    private static final String SUPPORT_STREAM_CLASS_NAME =
        PercObjectInputStreamSupport.class.getName().replace('.', '/');
    private static final String SUPPORT_PERC_CLASS_NAME =
        PercClassSupport.class.getName();
    private static final String SUPPORT_PERC_METHOD_NAME =
        PercMethodSupport.class.getName();
    private static final AtomicBoolean transformerInstalled = new AtomicBoolean();

    @Test
    void createsSerializableInstancesUsingPercSerializationConstructionRules() {
        Assumptions.assumeFalse(isNativeImageRuntime());
        installPercSupportRedirect();

        NonSerializableParent.constructorCalls.set(0);
        SerializableChild.constructorCalls.set(0);

        PercSerializationInstantiator<SerializableChild> instantiator =
            new PercSerializationInstantiator<>(SerializableChild.class);
        SerializableChild instance = instantiator.newInstance();

        Assertions.assertThat(instance).isNotNull();
        Assertions.assertThat(NonSerializableParent.constructorCalls).hasValue(1);
        Assertions.assertThat(SerializableChild.constructorCalls).hasValue(0);
        Assertions.assertThat(instance.parentState).isEqualTo("initialized-by-parent");
        Assertions.assertThat(instance.childState).isNull();
    }

    private static void installPercSupportRedirect() {
        if (!transformerInstalled.compareAndSet(false, true)) {
            return;
        }

        Instrumentation instrumentation = ByteBuddyAgent.install();
        ClassFileTransformer transformer = new PercSerializationInstantiatorTransformer();
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

    public static final class PercClassSupport {

        private final Class<?> type;

        private PercClassSupport(Class<?> type) {
            this.type = type;
        }

        public static PercClassSupport getPercClass(Class<?> type) {
            return new PercClassSupport(type);
        }

        public PercMethodSupport findMethod(String signature) {
            return new PercMethodSupport(type, signature);
        }
    }

    public static final class PercMethodSupport {

        private final Class<?> ownerType;
        private final String signature;

        private PercMethodSupport(Class<?> ownerType, String signature) {
            this.ownerType = ownerType;
            this.signature = signature;
        }
    }

    public static final class PercObjectInputStreamSupport {

        private PercObjectInputStreamSupport() {
        }

        public static Object noArgConstruct(
            Class<?> unserializableType,
            Object type,
            PercMethodSupport method
        ) {
            if (unserializableType != NonSerializableParent.class) {
                throw new IllegalArgumentException(
                    "Unexpected unserializable type: " + unserializableType.getName()
                );
            }
            if (type != SerializableChild.class) {
                throw new IllegalArgumentException("Unexpected type: " + type);
            }
            if (
                method.ownerType != NonSerializableParent.class
                    || !"<init>()V".equals(method.signature)
            ) {
                throw new IllegalArgumentException("Unexpected Perc method description");
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

    static final class PercSerializationInstantiatorTransformer implements ClassFileTransformer {

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
                    if ("java/io/ObjectInputStream".equals(internalName)) {
                        return SUPPORT_STREAM_CLASS_NAME;
                    }
                    return internalName;
                }
            });
            ClassVisitor classVisitor = new ClassVisitor(Opcodes.ASM9, classRemapper) {
                @Override
                public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
                ) {
                    MethodVisitor methodVisitor =
                        super.visitMethod(access, name, descriptor, signature, exceptions);
                    return new MethodVisitor(Opcodes.ASM9, methodVisitor) {
                        @Override
                        public void visitLdcInsn(Object value) {
                            if ("COM.newmonics.PercClassLoader.Method".equals(value)) {
                                super.visitLdcInsn(SUPPORT_PERC_METHOD_NAME);
                                return;
                            }
                            if ("COM.newmonics.PercClassLoader.PercClass".equals(value)) {
                                super.visitLdcInsn(SUPPORT_PERC_CLASS_NAME);
                                return;
                            }
                            super.visitLdcInsn(value);
                        }
                    };
                }
            };
            classReader.accept(classVisitor, 0);
            return classWriter.toByteArray();
        }
    }
}
