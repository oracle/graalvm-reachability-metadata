/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_classfilewriter.jboss_classfilewriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.classfilewriter.AccessFlag;
import org.jboss.classfilewriter.ClassFactory;
import org.jboss.classfilewriter.ClassFile;
import org.jboss.classfilewriter.ClassMethod;
import org.jboss.classfilewriter.JavaVersions;
import org.jboss.classfilewriter.code.BranchEnd;
import org.jboss.classfilewriter.code.CodeAttribute;
import org.jboss.classfilewriter.util.DescriptorUtils;
import org.junit.jupiter.api.Test;

public class CodeAttributeTest {
    private static final AtomicInteger GENERATED_CLASS_COUNTER = new AtomicInteger();
    private static final ClassFactory LOOKUP_CLASS_FACTORY = new SamePackageLookupClassFactory(CodeAttributeTest.class);

    @Test
    void mergesStackTypesByResolvingTheirCommonSupertype() throws Throwable {
        assumeRuntimeClassDefinitionSupport();

        final StringSelector selector = createSelectorUsingStackMerge();

        assertThat(selector.apply(1)).isEqualTo("first");
        assertThat(selector.apply(0)).isEqualTo("second");
    }

    @Test
    void mergesLocalVariableTypesByResolvingTheirCommonSupertype() throws Throwable {
        assumeRuntimeClassDefinitionSupport();

        final StringSelector selector = createSelectorUsingLocalVariableMerge();

        assertThat(selector.apply(1)).isEqualTo("first");
        assertThat(selector.apply(0)).isEqualTo("second");
    }

    private static StringSelector createSelectorUsingStackMerge() throws Throwable {
        final ClassFile classFile = newClassFile("StackMerge");
        addDefaultConstructor(classFile);

        final ClassMethod applyMethod = classFile.addMethod(
            AccessFlag.PUBLIC,
            "apply",
            DescriptorUtils.makeDescriptor(String.class),
            "I"
        );
        final CodeAttribute codeAttribute = applyMethod.getCodeAttribute();
        codeAttribute.iload(1);
        final BranchEnd zeroBranch = codeAttribute.ifeq();
        codeAttribute.newInstruction(FirstChoice.class);
        codeAttribute.dup();
        codeAttribute.invokespecial(FirstChoice.class.getConstructor());
        final BranchEnd end = codeAttribute.gotoInstruction();
        codeAttribute.branchEnd(zeroBranch);
        codeAttribute.newInstruction(SecondChoice.class);
        codeAttribute.dup();
        codeAttribute.invokespecial(SecondChoice.class.getConstructor());
        codeAttribute.branchEnd(end);
        codeAttribute.invokevirtual(CommonBase.class.getMethod("value"));
        codeAttribute.returnInstruction();

        return defineSelector(classFile);
    }

    private static StringSelector createSelectorUsingLocalVariableMerge() throws Throwable {
        final ClassFile classFile = newClassFile("LocalMerge");
        addDefaultConstructor(classFile);

        final ClassMethod applyMethod = classFile.addMethod(
            AccessFlag.PUBLIC,
            "apply",
            DescriptorUtils.makeDescriptor(String.class),
            "I"
        );
        final CodeAttribute codeAttribute = applyMethod.getCodeAttribute();
        codeAttribute.iload(1);
        final BranchEnd zeroBranch = codeAttribute.ifeq();
        codeAttribute.newInstruction(FirstChoice.class);
        codeAttribute.dup();
        codeAttribute.invokespecial(FirstChoice.class.getConstructor());
        codeAttribute.astore(2);
        final BranchEnd end = codeAttribute.gotoInstruction();
        codeAttribute.branchEnd(zeroBranch);
        codeAttribute.newInstruction(SecondChoice.class);
        codeAttribute.dup();
        codeAttribute.invokespecial(SecondChoice.class.getConstructor());
        codeAttribute.astore(2);
        codeAttribute.branchEnd(end);
        codeAttribute.aload(2);
        codeAttribute.invokevirtual(CommonBase.class.getMethod("value"));
        codeAttribute.returnInstruction();

        return defineSelector(classFile);
    }

    private static ClassFile newClassFile(final String suffix) {
        final String className = CodeAttributeTest.class.getPackageName()
            + ".CodeAttribute"
            + suffix
            + GENERATED_CLASS_COUNTER.incrementAndGet();
        return new ClassFile(
            className,
            AccessFlag.of(AccessFlag.PUBLIC, AccessFlag.SUPER),
            Object.class.getName(),
            JavaVersions.JAVA_7,
            CodeAttributeTest.class.getClassLoader(),
            LOOKUP_CLASS_FACTORY,
            StringSelector.class.getName()
        );
    }

    private static void assumeRuntimeClassDefinitionSupport() {
        assumeFalse(isNativeImageRuntime(), "Runtime class definition is not supported in native image tests");
    }

    private static boolean isNativeImageRuntime() {
        return "runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"));
    }

    private static void addDefaultConstructor(final ClassFile classFile) throws NoSuchMethodException {
        final ClassMethod constructor = classFile.addMethod(AccessFlag.PUBLIC, "<init>", "V");
        final CodeAttribute codeAttribute = constructor.getCodeAttribute();
        codeAttribute.aload(0);
        codeAttribute.invokespecial(Object.class.getConstructor());
        codeAttribute.returnInstruction();
    }

    private static StringSelector defineSelector(final ClassFile classFile) throws Throwable {
        final Class<? extends StringSelector> generatedClass = classFile.define().asSubclass(StringSelector.class);
        return (StringSelector) MethodHandles.lookup()
            .findConstructor(generatedClass, MethodType.methodType(void.class))
            .invoke();
    }

    private static final class SamePackageLookupClassFactory implements ClassFactory {
        private final MethodHandles.Lookup lookup;

        private SamePackageLookupClassFactory(final Class<?> anchorClass) {
            try {
                this.lookup = MethodHandles.privateLookupIn(anchorClass, MethodHandles.lookup());
            } catch (final IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public Class<?> defineClass(
            final ClassLoader loader,
            final String name,
            final byte[] bytecode,
            final int off,
            final int len,
            final ProtectionDomain protectionDomain
        ) {
            final byte[] classBytes = off == 0 && len == bytecode.length
                ? bytecode
                : Arrays.copyOfRange(bytecode, off, off + len);
            try {
                return lookup.defineClass(classBytes);
            } catch (final IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    public interface StringSelector {
        String apply(int value);
    }

    public abstract static class CommonBase {
        public abstract String value();
    }

    public static final class FirstChoice extends CommonBase {
        @Override
        public String value() {
            return "first";
        }
    }

    public static final class SecondChoice extends CommonBase {
        @Override
        public String value() {
            return "second";
        }
    }
}
