/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javassist_javassist;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.bytecode.AccessFlag;
import javassist.bytecode.Bytecode;
import javassist.bytecode.ClassFile;
import javassist.bytecode.ConstPool;
import javassist.bytecode.MethodInfo;
import javassist.util.proxy.FactoryHelper;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static javassist.bytecode.Opcode.ARETURN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class FactoryHelperTest {
    private static final String BRIDGE_CLASS_NAME = "javassist.util.proxy.FactoryHelperCoverageBridge";

    @Test
    void isolatedLoaderRunsFactoryHelperInitializationPath() throws Exception {
        try {
            URL javassistLocation = findJavassistLocation();
            if (javassistLocation == null) {
                assertThat(FactoryHelper.typeIndex(Boolean.TYPE)).isZero();
            } else {
                try (FactoryHelperIsolatedClassLoader loader =
                        new FactoryHelperIsolatedClassLoader(javassistLocation)) {
                    Class<?> isolatedFactoryHelper = Class.forName(FactoryHelper.class.getName(), true, loader);
                    Method typeIndex = isolatedFactoryHelper.getMethod("typeIndex", Class.class);

                    assertThat(typeIndex.invoke(null, Boolean.TYPE)).isEqualTo(0);
                }
            }
        } catch (Error error) {
            verifyUnsupportedDynamicClassLoading(error);
        }
    }

    @Test
    void bridgeExercisesClassLookupHelper() throws Exception {
        try {
            FactoryHelperProbe probe = (FactoryHelperProbe) createBridgeClass().newInstance();

            assertThat(probe.lookup(String.class.getName())).isSameAs(String.class);
            assertThat(probe.lookup(byte[].class.getName())).isSameAs(byte[].class);
        } catch (Error error) {
            verifyUnsupportedDynamicClassLoading(error);
        }
    }

    @Test
    void exposesPrimitiveTypeMetadataUsedByProxyGeneration() {
        assertThat(FactoryHelper.typeIndex(Boolean.TYPE)).isZero();
        assertThat(FactoryHelper.typeIndex(Integer.TYPE)).isEqualTo(4);
        assertThat(FactoryHelper.typeIndex(Double.TYPE)).isEqualTo(7);
        assertThat(FactoryHelper.typeIndex(Void.TYPE)).isEqualTo(8);

        assertThat(FactoryHelper.primitiveTypes)
                .containsExactly(Boolean.TYPE, Byte.TYPE, Character.TYPE, Short.TYPE, Integer.TYPE,
                        Long.TYPE, Float.TYPE, Double.TYPE, Void.TYPE);
        assertThat(FactoryHelper.wrapperTypes)
                .containsExactly("java.lang.Boolean", "java.lang.Byte", "java.lang.Character", "java.lang.Short",
                        "java.lang.Integer", "java.lang.Long", "java.lang.Float", "java.lang.Double", "java.lang.Void");
        assertThat(FactoryHelper.wrapperDesc)
                .containsExactly("(Z)V", "(B)V", "(C)V", "(S)V", "(I)V", "(J)V", "(F)V", "(D)V");
        assertThat(FactoryHelper.unwarpMethods)
                .containsExactly("booleanValue", "byteValue", "charValue", "shortValue", "intValue", "longValue",
                        "floatValue", "doubleValue");
        assertThat(FactoryHelper.unwrapDesc)
                .containsExactly("()Z", "()B", "()C", "()S", "()I", "()J", "()F", "()D");
        assertThat(FactoryHelper.dataSize)
                .containsExactly(1, 1, 1, 1, 1, 2, 1, 2);
    }

    @Test
    void rejectsNonPrimitiveTypes() {
        assertThatThrownBy(() -> FactoryHelper.typeIndex(String.class))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("bad type:java.lang.String");
    }

    private static Class<?> createBridgeClass() throws Exception {
        ClassFile classFile = new ClassFile(false, BRIDGE_CLASS_NAME, Object.class.getName());
        classFile.setAccessFlags(AccessFlag.PUBLIC);
        classFile.addInterface(FactoryHelperProbe.class.getName());
        addDefaultConstructor(classFile);
        addLookupMethod(classFile);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        classFile.write(new DataOutputStream(bytes));

        ClassPool classPool = new ClassPool(true);
        CtClass bridgeClass = classPool.makeClass(new ByteArrayInputStream(bytes.toByteArray()));
        return classPool.toClass(
                bridgeClass,
                FactoryHelperTest.class.getClassLoader(),
                FactoryHelperTest.class.getProtectionDomain());
    }

    private static void addDefaultConstructor(ClassFile classFile) throws Exception {
        ConstPool constPool = classFile.getConstPool();
        MethodInfo constructor = new MethodInfo(constPool, "<init>", "()V");
        constructor.setAccessFlags(AccessFlag.PUBLIC);

        Bytecode bytecode = new Bytecode(constPool, 1, 1);
        bytecode.addAload(0);
        bytecode.addInvokespecial(Object.class.getName(), "<init>", "()V");
        bytecode.addReturn(null);

        constructor.addAttribute(bytecode.toCodeAttribute());
        classFile.addMethod(constructor);
    }

    private static void addLookupMethod(ClassFile classFile) throws Exception {
        ConstPool constPool = classFile.getConstPool();
        MethodInfo method = new MethodInfo(constPool, "lookup", "(Ljava/lang/String;)Ljava/lang/Class;");
        method.setAccessFlags(AccessFlag.PUBLIC);

        Bytecode bytecode = new Bytecode(constPool, 1, 2);
        bytecode.addAload(1);
        bytecode.addInvokestatic(
                "javassist.util.proxy.FactoryHelper",
                "class$",
                "(Ljava/lang/String;)Ljava/lang/Class;");
        bytecode.addOpcode(ARETURN);

        method.addAttribute(bytecode.toCodeAttribute());
        classFile.addMethod(method);
    }

    private static URL findJavassistLocation() {
        CodeSource codeSource = FactoryHelper.class.getProtectionDomain().getCodeSource();
        if (codeSource == null) {
            return null;
        }

        URL location = codeSource.getLocation();
        if (location == null) {
            return null;
        }

        String externalForm = location.toExternalForm();
        if (!externalForm.endsWith(".jar") && !externalForm.endsWith("/")) {
            return null;
        }
        return location;
    }

    private static void verifyUnsupportedDynamicClassLoading(Error error) {
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
            throw error;
        }
    }

    public interface FactoryHelperProbe {
        Class<?> lookup(String name) throws Exception;
    }

    private static final class FactoryHelperIsolatedClassLoader extends URLClassLoader {
        private FactoryHelperIsolatedClassLoader(URL javassistLocation) {
            super(new URL[] { javassistLocation }, FactoryHelperTest.class.getClassLoader());
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> loadedClass = findLoadedClass(name);
                if (loadedClass == null && name.startsWith("javassist.")) {
                    try {
                        loadedClass = findClass(name);
                    } catch (ClassNotFoundException ignored) {
                        loadedClass = null;
                    }
                }
                if (loadedClass == null) {
                    loadedClass = super.loadClass(name, false);
                }
                if (resolve) {
                    resolveClass(loadedClass);
                }
                return loadedClass;
            }
        }
    }
}
