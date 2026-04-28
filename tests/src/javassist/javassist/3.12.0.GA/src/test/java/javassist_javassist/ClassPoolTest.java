/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javassist_javassist;

import java.lang.reflect.Method;
import java.security.ProtectionDomain;

import javassist.ClassPool;
import javassist.CtClass;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassPoolTest {
    @Test
    void createsPoolWithDefaultSystemPath() {
        ClassPool classPool = ClassPool.getDefault();

        assertThat(classPool).isNotNull();
        assertThat(classPool.find(Object.class.getName())).isNotNull();
    }

    @Test
    void syntheticClassLookupResolvesClassPoolInitializationTypes() throws Exception {
        Method classLookup = ClassPool.class.getDeclaredMethod("class$", String.class);
        classLookup.setAccessible(true);

        assertThat(classLookup.invoke(null, String.class.getName())).isEqualTo(String.class);
        assertThat(classLookup.invoke(null, "[B")).isEqualTo(byte[].class);
        assertThat(classLookup.invoke(null, ProtectionDomain.class.getName())).isEqualTo(ProtectionDomain.class);
    }

    @Test
    void definesCtClassWithSuppliedClassLoader() throws Exception {
        ClassPool classPool = new ClassPool(true);
        String className = "javassist_javassist.generated.ClassPoolDefinedWithoutDomain";
        CtClass ctClass = classPool.makeClass(className);
        IsolatedClassLoader loader = new IsolatedClassLoader();

        Class<?> generatedClass = classPool.toClass(ctClass, loader, null);

        assertThat(generatedClass.getName()).isEqualTo(className);
        assertThat(generatedClass.getClassLoader()).isSameAs(loader);
    }

    @Test
    void definesCtClassWithProtectionDomain() throws Exception {
        ClassPool classPool = new ClassPool(true);
        String className = "javassist_javassist.generated.ClassPoolDefinedWithDomain";
        CtClass ctClass = classPool.makeClass(className);
        IsolatedClassLoader loader = new IsolatedClassLoader();
        ProtectionDomain domain = ClassPoolTest.class.getProtectionDomain();

        Class<?> generatedClass = classPool.toClass(ctClass, loader, domain);

        assertThat(generatedClass.getName()).isEqualTo(className);
        assertThat(generatedClass.getClassLoader()).isSameAs(loader);
    }

    private static class IsolatedClassLoader extends ClassLoader {
    }
}
