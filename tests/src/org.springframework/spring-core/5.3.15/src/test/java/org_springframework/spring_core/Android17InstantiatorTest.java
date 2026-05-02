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
import org.springframework.asm.FieldVisitor;
import org.springframework.asm.Label;
import org.springframework.asm.MethodVisitor;
import org.springframework.asm.Opcodes;
import org.springframework.asm.Type;
import org.springframework.objenesis.instantiator.android.Android17Instantiator;

public class Android17InstantiatorTest {

    private static final String TARGET_CLASS_NAME =
            "org/springframework/objenesis/instantiator/android/Android17Instantiator";
    private static final String OBJECT_STREAM_CLASS_INTERNAL_NAME = "java/io/ObjectStreamClass";
    private static final String SUPPORT_INTERNAL_NAME =
            AndroidObjectStreamClassSupport.class.getName().replace('.', '/');
    private static final String DYNAMIC_DEFINITION_PROBE_CLASS_NAME =
            "org_springframework/spring_core/Android17InstantiatorDynamicDefinitionProbe";
    private static final AtomicBoolean transformerInstalled = new AtomicBoolean();

    @Test
    void createsInstancesWithoutRunningConstructorsUsingAndroid17ConstructionHook() {
        try {
            installObjectStreamClassRedirect();
            ConstructorBypassedType.constructorCalls.set(0);

            Android17Instantiator<ConstructorBypassedType> instantiator =
                    new Android17Instantiator<>(ConstructorBypassedType.class);
            ConstructorBypassedType instance = instantiator.newInstance();

            assertThat(instance).isNotNull();
            assertThat(instance.state).isNull();
            assertThat(ConstructorBypassedType.constructorCalls).hasValue(0);
        } catch (IllegalStateException exception) {
            ignoreUnsupportedDynamicClassLoading(exception);
        } catch (Error error) {
            ignoreUnsupportedDynamicClassLoading(error);
        }
    }

    private static void installObjectStreamClassRedirect() {
        if (!transformerInstalled.compareAndSet(false, true)) {
            return;
        }

        Instrumentation instrumentation = ByteBuddyAgent.install();
        ClassFileTransformer transformer = new Android17InstantiatorTransformer();
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
                    Android17InstantiatorTest.class.getClassLoader()
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

    public static class ConstructorBypassedType implements Serializable {

        private static final long serialVersionUID = 1L;

        static final AtomicInteger constructorCalls = new AtomicInteger();

        transient String state;

        public ConstructorBypassedType() {
            constructorCalls.incrementAndGet();
            this.state = "initialized-by-constructor";
        }
    }

    public static final class AndroidObjectStreamClassSupport {

        private static final int OBJECT_CONSTRUCTOR_ID = 1;

        private AndroidObjectStreamClassSupport() {
        }

        public static Integer getConstructorId(Class<?> type) {
            assertThat(type).isEqualTo(Object.class);
            return OBJECT_CONSTRUCTOR_ID;
        }

        public static Object newInstance(Class<?> type, int constructorId) {
            assertThat(type).isEqualTo(ConstructorBypassedType.class);
            assertThat(constructorId).isEqualTo(OBJECT_CONSTRUCTOR_ID);
            ConstructorBypassedType template = new ConstructorBypassedType();
            ConstructorBypassedType.constructorCalls.set(0);
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

    private static byte[] transformObjectStreamClassReferences(byte[] bytecode) {
        ClassReader classReader = new ClassReader(bytecode);
        ClassWriter classWriter = new ClassWriter(classReader, 0);
        classReader.accept(new ObjectStreamClassRedirectingVisitor(classWriter), 0);
        return classWriter.toByteArray();
    }

    private static String remapInternalName(String internalName) {
        if (OBJECT_STREAM_CLASS_INTERNAL_NAME.equals(internalName)) {
            return SUPPORT_INTERNAL_NAME;
        }
        return internalName;
    }

    private static String remapDescriptor(String descriptor) {
        if (descriptor == null) {
            return null;
        }
        return descriptor.replace(
                "L" + OBJECT_STREAM_CLASS_INTERNAL_NAME + ";",
                "L" + SUPPORT_INTERNAL_NAME + ";"
        );
    }

    private static Object remapFrameEntry(Object entry) {
        if (entry instanceof String) {
            return remapInternalName((String) entry);
        }
        return entry;
    }

    private static Object[] remapFrameEntries(Object[] entries, int count) {
        if (entries == null) {
            return null;
        }
        Object[] remappedEntries = new Object[count];
        for (int i = 0; i < count; i++) {
            remappedEntries[i] = remapFrameEntry(entries[i]);
        }
        return remappedEntries;
    }

    private static Object remapValue(Object value) {
        if (value instanceof Type) {
            Type type = (Type) value;
            if (type.getSort() == Type.OBJECT) {
                return Type.getObjectType(remapInternalName(type.getInternalName()));
            }
            if (type.getSort() == Type.METHOD) {
                return Type.getMethodType(remapDescriptor(type.getDescriptor()));
            }
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

    static final class Android17InstantiatorTransformer implements ClassFileTransformer {

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
            return transformObjectStreamClassReferences(classfileBuffer);
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

    private static final class ObjectStreamClassRedirectingVisitor extends ClassVisitor {

        private ObjectStreamClassRedirectingVisitor(ClassVisitor classVisitor) {
            super(Opcodes.ASM9, classVisitor);
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            return super.visitField(access, name, remapDescriptor(descriptor), signature, remapValue(value));
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                String[] exceptions) {

            MethodVisitor methodVisitor = super.visitMethod(access, name, remapDescriptor(descriptor), signature,
                    exceptions);
            return new ObjectStreamClassRedirectingMethodVisitor(methodVisitor);
        }
    }

    private static final class ObjectStreamClassRedirectingMethodVisitor extends MethodVisitor {

        private ObjectStreamClassRedirectingMethodVisitor(MethodVisitor methodVisitor) {
            super(Opcodes.ASM9, methodVisitor);
        }

        @Override
        public void visitTypeInsn(int opcode, String type) {
            super.visitTypeInsn(opcode, remapInternalName(type));
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
            super.visitFieldInsn(opcode, remapInternalName(owner), name, remapDescriptor(descriptor));
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            super.visitMethodInsn(opcode, remapInternalName(owner), name, remapDescriptor(descriptor), isInterface);
        }

        @Override
        public void visitLdcInsn(Object value) {
            super.visitLdcInsn(remapValue(value));
        }

        @Override
        public void visitFrame(int type, int numLocal, Object[] local, int numStack, Object[] stack) {
            super.visitFrame(type, numLocal, remapFrameEntries(local, numLocal), numStack,
                    remapFrameEntries(stack, numStack));
        }

        @Override
        public void visitLocalVariable(String name, String descriptor, String signature, Label start, Label end,
                int index) {

            super.visitLocalVariable(name, remapDescriptor(descriptor), signature, start, end, index);
        }
    }
}
