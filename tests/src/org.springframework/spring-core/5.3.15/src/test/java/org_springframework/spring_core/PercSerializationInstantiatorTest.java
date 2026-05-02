/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.security.ProtectionDomain;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import net.bytebuddy.agent.ByteBuddyAgent;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.springframework.asm.ClassReader;
import org.springframework.asm.ClassVisitor;
import org.springframework.asm.ClassWriter;
import org.springframework.asm.MethodVisitor;
import org.springframework.asm.Opcodes;
import org.springframework.asm.Type;
import org.springframework.objenesis.instantiator.perc.PercSerializationInstantiator;

public class PercSerializationInstantiatorTest {

    private static final String TARGET_CLASS_NAME =
            "org/springframework/objenesis/instantiator/perc/PercSerializationInstantiator";
    private static final String OBJECT_INPUT_STREAM_INTERNAL_NAME = "java/io/ObjectInputStream";
    private static final String SUPPORT_STREAM_INTERNAL_NAME =
            PercObjectInputStreamSupport.class.getName().replace('.', '/');
    private static final String PERC_CLASS_NAME = "COM.newmonics.PercClassLoader.PercClass";
    private static final String SUPPORT_PERC_CLASS_NAME = PercClassSupport.class.getName();
    private static final String PERC_METHOD_NAME = "COM.newmonics.PercClassLoader.Method";
    private static final String SUPPORT_PERC_METHOD_NAME = PercMethodSupport.class.getName();
    private static final String DYNAMIC_DEFINITION_PROBE_CLASS_NAME =
            "org_springframework/spring_core/PercSerializationInstantiatorDynamicDefinitionProbe";
    private static final AtomicBoolean transformerInstalled = new AtomicBoolean();

    @Test
    void createsSerializableInstancesUsingPercSerializationConstructionRules() {
        try {
            installPercSupportRedirect();
            NonSerializableParent.constructorCalls.set(0);
            SerializableChild.constructorCalls.set(0);

            PercSerializationInstantiator<SerializableChild> instantiator =
                    new PercSerializationInstantiator<>(SerializableChild.class);
            SerializableChild instance = instantiator.newInstance();

            assertThat(instance).isNotNull();
            assertThat(NonSerializableParent.constructorCalls).hasValue(1);
            assertThat(SerializableChild.constructorCalls).hasValue(0);
            assertThat(instance.parentState).isEqualTo("initialized-by-parent");
            assertThat(instance.childState).isNull();
        } catch (IllegalStateException exception) {
            ignoreUnsupportedDynamicClassLoading(exception);
        } catch (Error error) {
            ignoreUnsupportedDynamicClassLoading(error);
        }
    }

    private static void installPercSupportRedirect() {
        if (!transformerInstalled.compareAndSet(false, true)) {
            return;
        }

        Instrumentation instrumentation = ByteBuddyAgent.install();
        ClassFileTransformer transformer = new PercSerializationInstantiatorTransformer();
        instrumentation.addTransformer(transformer, true);
        retransformAlreadyLoadedInstantiator(instrumentation);
    }

    private static void retransformAlreadyLoadedInstantiator(Instrumentation instrumentation) {
        for (Class<?> loadedClass : instrumentation.getAllLoadedClasses()) {
            if (TARGET_CLASS_NAME.equals(loadedClass.getName().replace('.', '/'))
                    && instrumentation.isModifiableClass(loadedClass)) {
                try {
                    instrumentation.retransformClasses(loadedClass);
                } catch (UnmodifiableClassException exception) {
                    throw new IllegalStateException(exception);
                }
            }
        }
    }

    private static void ignoreUnsupportedDynamicClassLoading(IllegalStateException exception) {
        Throwable cause = exception.getCause();
        if (cause instanceof Error && NativeImageSupport.isUnsupportedFeatureError((Error) cause)) {
            return;
        }
        verifyDynamicClassDefinitionIsUnsupported(exception);
    }

    private static void verifyDynamicClassDefinitionIsUnsupported(IllegalStateException exception) {
        try {
            DynamicDefinitionProbeClassLoader classLoader = new DynamicDefinitionProbeClassLoader(
                    PercSerializationInstantiatorTest.class.getClassLoader()
            );
            classLoader.defineProbeClass();
        } catch (Error error) {
            ignoreUnsupportedDynamicClassLoading(error);
            return;
        }
        throw exception;
    }

    private static void ignoreUnsupportedDynamicClassLoading(Error error) {
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
            throw error;
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

        public static Object noArgConstruct(Class<?> unserializableType, Object type, PercMethodSupport method) {
            assertThat(unserializableType).isEqualTo(NonSerializableParent.class);
            assertThat(type).isEqualTo(SerializableChild.class);
            assertThat(method.ownerType).isEqualTo(NonSerializableParent.class);
            assertThat(method.signature).isEqualTo("<init>()V");

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

    private static byte[] transformPercSupportReferences(byte[] bytecode) {
        ClassReader classReader = new ClassReader(bytecode);
        ClassWriter classWriter = new ClassWriter(classReader, 0);
        classReader.accept(new PercSupportRedirectingVisitor(classWriter), 0);
        return classWriter.toByteArray();
    }

    private static String remapInternalName(String internalName) {
        if (OBJECT_INPUT_STREAM_INTERNAL_NAME.equals(internalName)) {
            return SUPPORT_STREAM_INTERNAL_NAME;
        }
        return internalName;
    }

    private static Object remapValue(Object value) {
        if (value instanceof String) {
            return remapStringValue((String) value);
        }
        if (value instanceof Type) {
            Type type = (Type) value;
            if (type.getSort() == Type.OBJECT) {
                return Type.getObjectType(remapInternalName(type.getInternalName()));
            }
        }
        return value;
    }

    private static Object remapStringValue(String value) {
        if (PERC_CLASS_NAME.equals(value)) {
            return SUPPORT_PERC_CLASS_NAME;
        }
        if (PERC_METHOD_NAME.equals(value)) {
            return SUPPORT_PERC_METHOD_NAME;
        }
        return value;
    }

    private static byte[] createDynamicDefinitionProbeBytecode() {
        ClassWriter classWriter = new ClassWriter(0);
        classWriter.visit(
                Opcodes.V1_8,
                Opcodes.ACC_PUBLIC,
                DYNAMIC_DEFINITION_PROBE_CLASS_NAME,
                null,
                Type.getInternalName(Object.class),
                null
        );
        MethodVisitor constructor = classWriter.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                Type.getInternalName(Object.class),
                "<init>",
                "()V",
                false
        );
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(1, 1);
        constructor.visitEnd();
        classWriter.visitEnd();
        return classWriter.toByteArray();
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
            return transformPercSupportReferences(classfileBuffer);
        }
    }

    private static final class DynamicDefinitionProbeClassLoader extends ClassLoader {

        private DynamicDefinitionProbeClassLoader(ClassLoader parent) {
            super(parent);
        }

        private void defineProbeClass() {
            byte[] bytecode = createDynamicDefinitionProbeBytecode();
            String className = DYNAMIC_DEFINITION_PROBE_CLASS_NAME.replace('/', '.');
            defineClass(className, bytecode, 0, bytecode.length);
        }
    }

    private static final class PercSupportRedirectingVisitor extends ClassVisitor {

        private PercSupportRedirectingVisitor(ClassVisitor classVisitor) {
            super(Opcodes.ASM9, classVisitor);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                String[] exceptions) {

            MethodVisitor methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);
            return new PercSupportRedirectingMethodVisitor(methodVisitor);
        }
    }

    private static final class PercSupportRedirectingMethodVisitor extends MethodVisitor {

        private PercSupportRedirectingMethodVisitor(MethodVisitor methodVisitor) {
            super(Opcodes.ASM9, methodVisitor);
        }

        @Override
        public void visitLdcInsn(Object value) {
            super.visitLdcInsn(remapValue(value));
        }
    }
}
