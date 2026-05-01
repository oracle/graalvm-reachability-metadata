/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity_dep;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collections;

import junit.framework.Test;
import junit.framework.TestResult;
import junit.framework.TestSuite;

import org.apache.velocity.test.IntrospectorTestCase2;
import org.graalvm.internal.tck.NativeImageSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class IntrospectorTestCase2Test {
    private static final String TEST_CASE_CLASS_NAME = "org.apache.velocity.test.IntrospectorTestCase2";

    @org.junit.jupiter.api.Test
    void resolvesMostSpecificMethodAndRejectsAmbiguousOverload() {
        IntrospectorTestCase2 testCase = new IntrospectorTestCase2("IntrospectorTestCase2");

        testCase.runTest();
    }

    @org.junit.jupiter.api.Test
    void freshClassLoaderRunsWithEmptyCompilerClassCache() throws Exception {
        try {
            URL codeSource = IntrospectorTestCase2.class.getProtectionDomain().getCodeSource().getLocation();
            try (URLClassLoader classLoader = new IsolatedIntrospectorTestCase2ClassLoader(
                    new URL[] { codeSource },
                    IntrospectorTestCase2.class.getClassLoader())) {
                Class<?> testClass = classLoader.loadClass(TEST_CASE_CLASS_NAME);
                Test test = TestSuite.createTest(testClass, "runTest");
                TestResult result = new TestResult();

                test.run(result);

                assertEquals(0, result.errorCount(), Collections.list(result.errors()).toString());
                assertEquals(0, result.failureCount(), Collections.list(result.failures()).toString());
            }
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static final class IsolatedIntrospectorTestCase2ClassLoader extends URLClassLoader {
        private IsolatedIntrospectorTestCase2ClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (isIntrospectorTestCase2Class(name)) {
                synchronized (getClassLoadingLock(name)) {
                    Class<?> loadedClass = findLoadedClass(name);
                    if (loadedClass == null) {
                        loadedClass = findClass(name);
                    }
                    if (resolve) {
                        resolveClass(loadedClass);
                    }
                    return loadedClass;
                }
            }
            return super.loadClass(name, resolve);
        }

        private static boolean isIntrospectorTestCase2Class(String name) {
            return name.equals(TEST_CASE_CLASS_NAME) || name.startsWith(TEST_CASE_CLASS_NAME + "$");
        }
    }
}
