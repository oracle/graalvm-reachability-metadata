/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cglib.cglib_nodep;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

import net.sf.cglib.asm.ClassWriter;
import net.sf.cglib.asm.MethodVisitor;
import net.sf.cglib.asm.Opcodes;
import net.sf.cglib.core.ReflectUtils;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class ReflectUtilsTest {
    @Test
    void findsConstructorsAndMethodsFromTextDescriptors() {
        Constructor<?> javaLangConstructor = ReflectUtils.findConstructor("String()");
        Method primitiveArrayMethod = ReflectUtils.findMethod("java.util.Arrays.toString(int[])");

        assertThat(javaLangConstructor.getDeclaringClass()).isEqualTo(String.class);
        assertThat(primitiveArrayMethod.getName()).isEqualTo("toString");
        assertThat(primitiveArrayMethod.getParameterTypes()).containsExactly(int[].class);
    }

    @Test
    void createsInstancesThroughDeclaredConstructors() {
        ConstructedSample sample = (ConstructedSample) ReflectUtils.newInstance(
                ConstructedSample.class,
                new Class[] { String.class },
                new Object[] { "created" });

        assertThat(sample.value()).isEqualTo("created");
    }

    @Test
    void findsDeclaredMethodsAcrossClassHierarchy() throws NoSuchMethodException {
        Method inheritedMethod = ReflectUtils.findDeclaredMethod(
                ChildSample.class,
                "inheritedSecret",
                new Class[] { String.class });

        assertThat(inheritedMethod.getDeclaringClass()).isEqualTo(ParentSample.class);
        assertThat(inheritedMethod.getName()).isEqualTo("inheritedSecret");
    }

    @Test
    void addsDeclaredMethodsFromClassHierarchyAndInterfaces() {
        List<Method> methods = ReflectUtils.addAllMethods(ChildSample.class, new ArrayList<Method>());

        assertThat(methods).extracting(Method::getName)
                .contains("childMethod", "inheritedSecret", "interfaceMethod");
    }

    @Test
    void findsTheSingleNewInstanceInterfaceMethod() {
        Method factoryMethod = ReflectUtils.findNewInstance(SampleFactory.class);

        assertThat(factoryMethod.getDeclaringClass()).isEqualTo(SampleFactory.class);
        assertThat(factoryMethod.getName()).isEqualTo("newInstance");
    }

    @Test
    void definesGeneratedClassWithProvidedLoader() throws Exception {
        String className = "cglib.cglib_nodep.ReflectUtilsDefinedSample";
        byte[] classBytes = createClassBytes(className);
        try (URLClassLoader loader = new URLClassLoader(new URL[0], ReflectUtilsTest.class.getClassLoader())) {
            try {
                Class<?> definedClass = ReflectUtils.defineClass(className, classBytes, loader);

                assertThat(definedClass.getName()).isEqualTo(className);
                assertThat(definedClass.getClassLoader()).isSameAs(loader);
            } catch (Error error) {
                if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                    throw error;
                }
            } catch (Exception exception) {
                Throwable cause = exception.getCause();
                if (!(cause instanceof Error) || !NativeImageSupport.isUnsupportedFeatureError((Error) cause)) {
                    throw exception;
                }
            }
        }
    }

    private static byte[] createClassBytes(String className) {
        String internalName = className.replace('.', '/');
        ClassWriter writer = new ClassWriter(0);
        writer.visit(
                Opcodes.V1_5,
                Opcodes.ACC_PUBLIC,
                internalName,
                null,
                "java/lang/Object",
                null);
        MethodVisitor constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V");
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(1, 1);
        constructor.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    public static class ConstructedSample {
        private final String value;

        private ConstructedSample(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }

    public static class ParentSample {
        private String inheritedSecret(String input) {
            return "parent-" + input;
        }
    }

    public interface SampleInterface {
        String interfaceMethod();
    }

    public static class ChildSample extends ParentSample implements SampleInterface {
        public String childMethod() {
            return "child";
        }

        public String interfaceMethod() {
            return "interface";
        }
    }

    public interface SampleFactory {
        Object newInstance();
    }
}
