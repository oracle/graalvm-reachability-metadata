/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javassist.javassist;

import static org.assertj.core.api.Assertions.assertThat;

import javassist.CannotCompileException;
import javassist.ClassClassPath;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtNewMethod;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class ClassPoolTest {
    private static final String GENERATED_CLASS_NAME = "example.ClassPoolGeneratedFixture";

    @Test
    void definesGeneratedClassWithSuppliedClassLoader() throws Throwable {
        ClassPool classPool = newInitializedClassPool();
        CtClass generatedClass = makeGeneratedClass(classPool);
        GeneratedClassLoader loader = new GeneratedClassLoader();

        try {
            Class<?> loadedClass = classPool.toClass(generatedClass, loader);

            assertThat(loadedClass.getName()).isEqualTo(GENERATED_CLASS_NAME);
            assertThat(loadedClass.getClassLoader()).isSameAs(loader);
        } catch (CannotCompileException exception) {
            rethrowUnlessUnsupportedDynamicClassLoading(exception);
        } catch (Error error) {
            rethrowUnlessUnsupportedDynamicClassLoading(error);
        } finally {
            generatedClass.detach();
        }
    }

    private static ClassPool newInitializedClassPool() {
        ClassPool classPool = new ClassPool(true);
        classPool.insertClassPath(new ClassClassPath(ClassPoolTest.class));
        return classPool;
    }

    private static CtClass makeGeneratedClass(ClassPool classPool) throws Exception {
        CtClass generatedClass = classPool.makeClass(GENERATED_CLASS_NAME);
        generatedClass.addMethod(CtNewMethod.make(
                """
                public String message() {
                    return "class-pool-generated";
                }
                """,
                generatedClass));
        return generatedClass;
    }

    private static void rethrowUnlessUnsupportedDynamicClassLoading(Throwable throwable) throws Throwable {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof Error error && NativeImageSupport.isUnsupportedFeatureError(error)) {
                return;
            }
            current = current.getCause();
        }

        throw throwable;
    }

    private static final class GeneratedClassLoader extends ClassLoader {
        private GeneratedClassLoader() {
            super(ClassPoolTest.class.getClassLoader());
        }
    }
}
