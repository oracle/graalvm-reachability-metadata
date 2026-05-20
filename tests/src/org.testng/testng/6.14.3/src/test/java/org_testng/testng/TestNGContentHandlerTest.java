/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testng.testng;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.testng.ITestObjectFactory;
import org.testng.xml.Parser;
import org.testng.xml.TestNGContentHandler;
import org.testng.xml.XmlSuite;
import org.xml.sax.InputSource;

public class TestNGContentHandlerTest {
    private static final char INNER_CLASS_SEPARATOR = 36;

    @Test
    void parsesSuiteDtdAndInstantiatesCustomObjectFactory() throws Exception {
        String xml = """
                <!DOCTYPE suite SYSTEM "http://testng.org/testng-1.0.dtd">
                <suite name="factory-suite" object-factory="%s"/>
                """.formatted(RecordingObjectFactory.class.getName());

        Collection<XmlSuite> suites = new Parser(inputStream(xml)).parse();

        XmlSuite suite = suites.iterator().next();
        assertThat(suite.getName()).isEqualTo("factory-suite");
        assertThat(suite.getObjectFactory()).isInstanceOf(RecordingObjectFactory.class);
    }

    @Test
    void fallsBackToContextClassLoaderWhenHandlerClassLoaderCannotFindDtd() throws Exception {
        try {
            TestNGContentHandler handler = newResourceHidingContentHandler();
            ClassLoader originalLoader = Thread.currentThread().getContextClassLoader();
            try {
                Thread.currentThread().setContextClassLoader(new DtdOnlyClassLoader(originalLoader));

                InputSource source = handler.resolveEntity(null, Parser.TESTNG_DTD_URL);

                assertThat(source).isNotNull();
                assertThat(source.getByteStream()).isNotNull();
            } finally {
                Thread.currentThread().setContextClassLoader(originalLoader);
            }
        } catch (Error e) {
            if (!NativeImageSupport.isUnsupportedFeatureError(e)) {
                throw e;
            }
        }
    }

    private static TestNGContentHandler newResourceHidingContentHandler() throws Exception {
        String targetClassName = TestNGContentHandlerTest.class.getName()
                + INNER_CLASS_SEPARATOR + "ResourceHidingContentHandler";
        ResourceHidingClassLoader loader = new ResourceHidingClassLoader(
                TestNGContentHandlerTest.class.getClassLoader(),
                targetClassName);
        try {
            return loader.loadClass(targetClassName)
                    .asSubclass(TestNGContentHandler.class)
                    .getConstructor()
                    .newInstance();
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw e;
        }
    }

    private static InputStream inputStream(String xml) {
        return new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
    }

    public static final class RecordingObjectFactory implements ITestObjectFactory {
        public RecordingObjectFactory() {
        }
    }

    public static final class ResourceHidingContentHandler extends TestNGContentHandler {
        public ResourceHidingContentHandler() {
            super("classpath-suite.xml", true);
        }
    }

    private static final class DtdOnlyClassLoader extends ClassLoader {
        private DtdOnlyClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            if (Parser.TESTNG_DTD.equals(name)) {
                return inputStream("<!ELEMENT suite ANY>");
            }
            return super.getResourceAsStream(name);
        }
    }

    private static final class ResourceHidingClassLoader extends ClassLoader {
        private final String targetClassName;

        private ResourceHidingClassLoader(ClassLoader parent, String targetClassName) {
            super(parent);
            this.targetClassName = targetClassName;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!targetClassName.equals(name)) {
                return super.loadClass(name, resolve);
            }
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

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            String resourceName = name.replace('.', '/') + ".class";
            try (InputStream inputStream = getParent().getResourceAsStream(resourceName)) {
                if (inputStream == null) {
                    throw new ClassNotFoundException(name);
                }
                byte[] bytes = inputStream.readAllBytes();
                return defineClass(name, bytes, 0, bytes.length);
            } catch (IOException e) {
                throw new ClassNotFoundException(name, e);
            }
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            if (Parser.TESTNG_DTD.equals(name)) {
                return null;
            }
            return super.getResourceAsStream(name);
        }
    }
}
