/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jdom.jdom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ServiceLoader;
import java.util.concurrent.Callable;

import org.apache.xerces.parsers.DOMParser;
import org.graalvm.internal.tck.NativeImageSupport;
import org.jdom.adapters.XML4JDOMAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class XML4JDOMAdapterTest {
    private static final String ISOLATED_CALLABLE_CLASS_NAME =
            XML4JDOMAdapterTest.class.getName() + "$IsolatedXml4jDomAdapterCallable";
    private static final String JDOM_PACKAGE = "org.jdom.";
    private static final String XERCES_PARSER_PACKAGE = "org.apache.xerces.parsers.";

    @Test
    void parsesValidatingDocumentThroughXml4jParserReflection() throws Exception {
        DOMParser.resetInvocationCounts();
        XML4JDOMAdapter adapter = new XML4JDOMAdapter();
        String xml = """
                <catalog>
                    <entry sku="xml4j">XML4J</entry>
                </catalog>
                """;

        Document document = parse(adapter, xml, true);

        Element root = document.getDocumentElement();
        Element entry = (Element) root.getElementsByTagName("entry").item(0);
        assertThat(root.getTagName()).isEqualTo("catalog");
        assertThat(entry.getAttribute("sku")).isEqualTo("xml4j");
        assertThat(entry.getTextContent()).isEqualTo("XML4J");
        assertThat(DOMParser.getInstanceCount()).isEqualTo(1);
        assertThat(DOMParser.getSetFeatureCount()).isEqualTo(2);
        assertThat(DOMParser.getSetErrorHandlerCount()).isEqualTo(1);
        assertThat(DOMParser.getParseCount()).isEqualTo(1);
        assertThat(DOMParser.getDocumentAccessCount()).isEqualTo(1);
    }

    @Test
    void parsesDocumentThroughXml4jAdapterLoadedInIsolatedClassLoader() throws Exception {
        IsolatedClassLoader classLoader = new IsolatedClassLoader(XML4JDOMAdapterTest.class.getClassLoader());

        try {
            Callable<?> action = ServiceLoader.load(Callable.class, classLoader).stream()
                    .filter(provider -> provider.type().getName().equals(ISOLATED_CALLABLE_CLASS_NAME))
                    .map(ServiceLoader.Provider::get)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "Expected isolated XML4J DOM adapter callable provider"));

            assertThat(action.call()).isEqualTo("isolated-xml4j:1:2:0:1:1");
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    @Test
    void reportsSaxParseFailuresFromXml4jParserReflection() {
        DOMParser.resetInvocationCounts();
        XML4JDOMAdapter adapter = new XML4JDOMAdapter();
        Executable parseMalformedXml = () -> parse(adapter, "<root><broken></root>", false);

        IOException exception = assertThrows(IOException.class, parseMalformedXml);
        assertThat(exception.getMessage()).contains("Error on line");
        assertThat(DOMParser.getInstanceCount()).isEqualTo(1);
        assertThat(DOMParser.getSetFeatureCount()).isEqualTo(2);
        assertThat(DOMParser.getSetErrorHandlerCount()).isZero();
        assertThat(DOMParser.getParseCount()).isEqualTo(1);
        assertThat(DOMParser.getDocumentAccessCount()).isZero();
    }

    public static final class IsolatedXml4jDomAdapterCallable implements Callable<String> {
        @Override
        public String call() throws Exception {
            DOMParser.resetInvocationCounts();
            XML4JDOMAdapter adapter = new XML4JDOMAdapter();
            byte[] xmlBytes = "<root id=\"isolated-xml4j\" />".getBytes(StandardCharsets.UTF_8);
            Document document;
            try (InputStream inputStream = new ByteArrayInputStream(xmlBytes)) {
                document = adapter.getDocument(inputStream, false);
            }

            return document.getDocumentElement().getAttribute("id")
                    + ":" + DOMParser.getInstanceCount()
                    + ":" + DOMParser.getSetFeatureCount()
                    + ":" + DOMParser.getSetErrorHandlerCount()
                    + ":" + DOMParser.getParseCount()
                    + ":" + DOMParser.getDocumentAccessCount();
        }
    }

    private static Document parse(XML4JDOMAdapter adapter, String xml, boolean validate) throws IOException {
        byte[] xmlBytes = xml.getBytes(StandardCharsets.UTF_8);
        try (InputStream inputStream = new ByteArrayInputStream(xmlBytes)) {
            return adapter.getDocument(inputStream, validate);
        }
    }

    private static final class IsolatedClassLoader extends ClassLoader {
        private IsolatedClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> loadedClass = findLoadedClass(name);
                if (loadedClass == null) {
                    loadedClass = loadUnresolvedClass(name);
                }
                if (resolve) {
                    resolveClass(loadedClass);
                }
                return loadedClass;
            }
        }

        private Class<?> loadUnresolvedClass(String name) throws ClassNotFoundException {
            if (!isChildFirst(name)) {
                return super.loadClass(name, false);
            }

            try {
                return findClass(name);
            } catch (ClassNotFoundException ignored) {
                return super.loadClass(name, false);
            }
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            String resourceName = name.replace('.', '/') + ".class";

            try (InputStream inputStream = getParent().getResourceAsStream(resourceName)) {
                if (inputStream == null) {
                    throw new ClassNotFoundException(name);
                }
                byte[] classBytes = inputStream.readAllBytes();
                return defineClass(name, classBytes, 0, classBytes.length);
            } catch (IOException exception) {
                throw new ClassNotFoundException(name, exception);
            }
        }

        private boolean isChildFirst(String className) {
            return className.startsWith(JDOM_PACKAGE)
                    || className.startsWith(XERCES_PARSER_PACKAGE)
                    || className.equals(ISOLATED_CALLABLE_CLASS_NAME);
        }
    }
}
