/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javassist_javassist;

import java.net.URL;
import java.net.URLClassLoader;

import javassist.ClassPool;
import javassist.CtClass;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassPoolTest {
    private static final String GENERATED_CLASS_NAME = "javassist_javassist.ClassPoolGeneratedTarget";

    @Test
    void toClassDefinesGeneratedClassWithSuppliedClassLoader() throws Exception {
        try (URLClassLoader targetLoader = new URLClassLoader(
                new URL[0],
                ClassPoolTest.class.getClassLoader())) {
            ClassPool classPool = new ClassPool(true);
            CtClass generatedClass = classPool.makeClass(GENERATED_CLASS_NAME);

            Class<?> loadedClass = classPool.toClass(
                    generatedClass,
                    targetLoader,
                    ClassPoolTest.class.getProtectionDomain());

            assertThat(loadedClass.getName()).isEqualTo(GENERATED_CLASS_NAME);
            assertThat(loadedClass.getClassLoader()).isSameAs(targetLoader);
        } catch (Error error) {
            verifyUnsupportedDynamicClassLoading(error);
        }
    }

    @Test
    void getDefaultInitializesPoolAndFindsPrimitiveClasses() throws Exception {
        ClassPool classPool = ClassPool.getDefault();

        CtClass intClass = classPool.get(int.class.getName());

        assertThat(intClass.getName()).isEqualTo(int.class.getName());
    }

    private static void verifyUnsupportedDynamicClassLoading(Error error) {
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
            throw error;
        }
    }
}
