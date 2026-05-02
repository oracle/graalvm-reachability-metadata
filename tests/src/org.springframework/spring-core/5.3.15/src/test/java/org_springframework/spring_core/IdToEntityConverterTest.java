/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;

import net.bytebuddy.agent.ByteBuddyAgent;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.springframework.asm.ClassReader;
import org.springframework.asm.ClassVisitor;
import org.springframework.asm.ClassWriter;
import org.springframework.asm.MethodVisitor;
import org.springframework.asm.Opcodes;
import org.springframework.asm.Type;
import org.springframework.core.convert.support.DefaultConversionService;

public class IdToEntityConverterTest {
    private static final String TARGET_CLASS_NAME =
            "org/springframework/core/convert/support/IdToEntityConverter";
    private static final String CLASS_INTERNAL_NAME = Type.getInternalName(Class.class);
    private static final String SUPPORT_INTERNAL_NAME =
            IdToEntityConverterTest.class.getName().replace('.', '/');
    private static final String DYNAMIC_DEFINITION_PROBE_CLASS_NAME =
            "org_springframework/spring_core/IdToEntityConverterDynamicDefinitionProbe";

    @Test
    void fallsBackToPublicFinderMethodsWhenDeclaredMethodsAccessIsDenied() {
        try (InstalledTransform ignored = installDeclaredMethodsAccessDenyingTransform()) {
            DefaultConversionService conversionService = new DefaultConversionService();
            SecureLookupEntity entity = conversionService.convert("42", SecureLookupEntity.class);

            assertThat(entity).isNotNull();
            assertThat(entity.getId()).isEqualTo(42L);
        } catch (IllegalStateException exception) {
            ignoreUnsupportedDynamicClassLoading(exception);
        } catch (Error error) {
            ignoreUnsupportedDynamicClassLoading(error);
        }
    }

    public static Method[] denyDeclaredMethodLookup(Class<?> entityClass) {
        throw new SecurityException("declared method access denied for coverage test");
    }

    private static InstalledTransform installDeclaredMethodsAccessDenyingTransform() {
        Instrumentation instrumentation = ByteBuddyAgent.install();
        ClassFileTransformer transformer = new IdToEntityConverterTransformer();
        instrumentation.addTransformer(transformer, true);
        retransformAlreadyLoadedConverter(instrumentation);
        return new InstalledTransform(instrumentation, transformer);
    }

    private static void retransformAlreadyLoadedConverter(Instrumentation instrumentation) {
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
                    IdToEntityConverterTest.class.getClassLoader()
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

    private static byte[] transformDeclaredMethodsLookup(byte[] bytecode) {
        ClassReader classReader = new ClassReader(bytecode);
        ClassWriter classWriter = new ClassWriter(classReader, 0);
        classReader.accept(new DeclaredMethodsAccessDenyingVisitor(classWriter), 0);
        return classWriter.toByteArray();
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

    public static final class SecureLookupEntity {
        private final long id;

        private SecureLookupEntity(long id) {
            this.id = id;
        }

        public static SecureLookupEntity findSecureLookupEntity(Long id) {
            return new SecureLookupEntity(id);
        }

        private long getId() {
            return id;
        }
    }

    private static final class InstalledTransform implements AutoCloseable {
        private final Instrumentation instrumentation;
        private final ClassFileTransformer transformer;

        private InstalledTransform(Instrumentation instrumentation, ClassFileTransformer transformer) {
            this.instrumentation = instrumentation;
            this.transformer = transformer;
        }

        @Override
        public void close() {
            instrumentation.removeTransformer(transformer);
            retransformAlreadyLoadedConverter(instrumentation);
        }
    }

    private static final class IdToEntityConverterTransformer implements ClassFileTransformer {

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
            return transformDeclaredMethodsLookup(classfileBuffer);
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

    private static final class DeclaredMethodsAccessDenyingVisitor extends ClassVisitor {

        private DeclaredMethodsAccessDenyingVisitor(ClassVisitor classVisitor) {
            super(Opcodes.ASM9, classVisitor);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                String[] exceptions) {

            MethodVisitor methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);
            return new DeclaredMethodsAccessDenyingMethodVisitor(methodVisitor);
        }
    }

    private static final class DeclaredMethodsAccessDenyingMethodVisitor extends MethodVisitor {

        private DeclaredMethodsAccessDenyingMethodVisitor(MethodVisitor methodVisitor) {
            super(Opcodes.ASM9, methodVisitor);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            if (opcode == Opcodes.INVOKEVIRTUAL
                    && CLASS_INTERNAL_NAME.equals(owner)
                    && "getDeclaredMethods".equals(name)
                    && "()[Ljava/lang/reflect/Method;".equals(descriptor)) {
                super.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        SUPPORT_INTERNAL_NAME,
                        "denyDeclaredMethodLookup",
                        "(Ljava/lang/Class;)[Ljava/lang/reflect/Method;",
                        false
                );
                return;
            }
            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
        }
    }
}
