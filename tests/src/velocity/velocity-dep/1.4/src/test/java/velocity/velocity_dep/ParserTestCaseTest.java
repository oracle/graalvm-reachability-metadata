/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity_dep;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import junit.framework.TestSuite;
import org.apache.velocity.test.ParserTestCase;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class ParserTestCaseTest {
    @Test
    void buildsJUnit3SuiteForParserTests() throws Exception {
        final TestSuite suite = (TestSuite) ParserTestCase.suite();

        assertEquals(3, suite.countTestCases());
    }

    @Test
    void buildsJUnit3SuiteForParserTestsLoadedInFreshClassLoader() throws Exception {
        try {
            final ParserTestCaseClassLoader classLoader = new ParserTestCaseClassLoader(
                    ParserTestCase.class.getClassLoader());
            final Class<?> parserTestCaseClass = Class.forName(
                    ParserTestCaseClassLoader.PARSER_TEST_CASE_CLASS_NAME,
                    true,
                    classLoader);

            final TestSuite suite = (TestSuite) parserTestCaseClass.getMethod("suite").invoke(null);

            assertEquals(3, suite.countTestCases());
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static final class ParserTestCaseClassLoader extends ClassLoader {
        private static final String PARSER_TEST_CASE_CLASS_NAME = "org.apache.velocity.test.ParserTestCase";
        private static final String PARSER_TEST_CASE_RESOURCE_NAME =
                "org/apache/velocity/test/ParserTestCase.class";

        private ParserTestCaseClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!PARSER_TEST_CASE_CLASS_NAME.equals(name)) {
                return super.loadClass(name, resolve);
            }

            synchronized (getClassLoadingLock(name)) {
                Class<?> loadedClass = findLoadedClass(name);
                if (loadedClass == null) {
                    loadedClass = defineParserTestCaseClass();
                }
                if (resolve) {
                    resolveClass(loadedClass);
                }
                return loadedClass;
            }
        }

        private Class<?> defineParserTestCaseClass() throws ClassNotFoundException {
            try {
                final byte[] classBytes = readParserTestCaseClassBytes();
                return defineClass(PARSER_TEST_CASE_CLASS_NAME, classBytes, 0, classBytes.length);
            } catch (IOException exception) {
                throw new ClassNotFoundException(PARSER_TEST_CASE_CLASS_NAME, exception);
            }
        }

        private byte[] readParserTestCaseClassBytes() throws IOException, ClassNotFoundException {
            try (InputStream inputStream = getParent().getResourceAsStream(PARSER_TEST_CASE_RESOURCE_NAME)) {
                if (inputStream == null) {
                    throw new ClassNotFoundException(PARSER_TEST_CASE_CLASS_NAME);
                }
                final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                final byte[] buffer = new byte[4096];
                int bytesRead = inputStream.read(buffer);
                while (bytesRead != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    bytesRead = inputStream.read(buffer);
                }
                return outputStream.toByteArray();
            }
        }
    }
}
