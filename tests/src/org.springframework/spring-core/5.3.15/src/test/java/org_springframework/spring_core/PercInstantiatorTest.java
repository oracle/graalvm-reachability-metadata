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
import org.springframework.objenesis.instantiator.perc.PercInstantiator;

public class PercInstantiatorTest {

    private static final String TARGET_CLASS_NAME =
            "org/springframework/objenesis/instantiator/perc/PercInstantiator";
    private static final String OBJECT_INPUT_STREAM_INTERNAL_NAME = "java/io/ObjectInputStream";
    private static final String SUPPORT_INTERNAL_NAME =
            PercObjectInputStreamSupport.class.getName().replace('.', '/');
    private static final String DYNAMIC_DEFINITION_PROBE_CLASS_NAME =
            "org_springframework/spring_core/PercInstantiatorDynamicDefinitionProbe";
    private static final AtomicBoolean transformerInstalled = new AtomicBoolean();

    @Test
    void createsInstancesWithoutRunningConstructorsUsingPercConstructionHook() {
        try {
            installObjectInputStreamRedirect();
            ConstructorBypassedType.constructorCalls.set(0);

            PercInstantiator<ConstructorBypassedType> instantiator =
                    new PercInstantiator<>(ConstructorBypassedType.class);
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

    private static void installObjectInputStreamRedirect() {
        if (!transformerInstalled.compareAndSet(false, true)) {
            return;
        }

        Instrumentation instrumentation = ByteBuddyAgent.install();
        ClassFileTransformer transformer = new PercInstantiatorTransformer();
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
                    PercInstantiatorTest.class.getClassLoader()
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

    public static final class PercObjectInputStreamSupport {

        private PercObjectInputStreamSupport() {
        }

        public static Object newInstance(Class<?> type, boolean useConstructor) {
            assertThat(type).isEqualTo(ConstructorBypassedType.class);
            assertThat(useConstructor).isFalse();

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

    private static byte[] transformObjectInputStreamReferences(byte[] bytecode) {
        ClassReader classReader = new ClassReader(bytecode);
        ClassWriter classWriter = new ClassWriter(classReader, 0);
        classReader.accept(new ObjectInputStreamRedirectingVisitor(classWriter), 0);
        return classWriter.toByteArray();
    }

    private static String remapInternalName(String internalName) {
        if (OBJECT_INPUT_STREAM_INTERNAL_NAME.equals(internalName)) {
            return SUPPORT_INTERNAL_NAME;
        }
        return internalName;
    }

    private static Object remapValue(Object value) {
        if (value instanceof Type) {
            Type type = (Type) value;
            if (type.getSort() == Type.OBJECT) {
                return Type.getObjectType(remapInternalName(type.getInternalName()));
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

    static final class PercInstantiatorTransformer implements ClassFileTransformer {

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
            return transformObjectInputStreamReferences(classfileBuffer);
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

    private static final class ObjectInputStreamRedirectingVisitor extends ClassVisitor {

        private ObjectInputStreamRedirectingVisitor(ClassVisitor classVisitor) {
            super(Opcodes.ASM9, classVisitor);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                String[] exceptions) {

            MethodVisitor methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);
            return new ObjectInputStreamRedirectingMethodVisitor(methodVisitor);
        }
    }

    private static final class ObjectInputStreamRedirectingMethodVisitor extends MethodVisitor {

        private ObjectInputStreamRedirectingMethodVisitor(MethodVisitor methodVisitor) {
            super(Opcodes.ASM9, methodVisitor);
        }

        @Override
        public void visitLdcInsn(Object value) {
            super.visitLdcInsn(remapValue(value));
        }
    }
}
