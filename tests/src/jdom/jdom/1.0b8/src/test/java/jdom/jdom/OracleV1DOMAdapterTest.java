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

import oracle.xml.parser.XMLParser;

import org.graalvm.internal.tck.NativeImageSupport;
import org.jdom.adapters.OracleV1DOMAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class OracleV1DOMAdapterTest {
    private static final String ISOLATED_CALLABLE_CLASS_NAME =
            OracleV1DOMAdapterTest.class.getName() + "$IsolatedOracleV1DOMAdapterCallable";
    private static final String JDOM_PACKAGE = "org.jdom.";
    private static final String ORACLE_PARSER_PACKAGE = "oracle.xml.parser.";

    @Test
    void parsesNamespaceAwareDocumentThroughOracleV1ParserReflection() throws Exception {
        XMLParser.resetInvocationCounts();
        OracleV1DOMAdapter adapter = new OracleV1DOMAdapter();
        String xml = """
                <root xmlns="urn:jdom-oracle" xmlns:item="urn:jdom-oracle-item">
                    <item:child id="oracle-v1">value</item:child>
                </root>
                """;

        Document document = parse(adapter, xml);
        Document secondDocument = parse(adapter, xml);

        assertOracleV1Document(document, "value");
        assertOracleV1Document(secondDocument, "value");
        assertThat(XMLParser.getInstanceCount()).isEqualTo(2);
        assertThat(XMLParser.getParseCount()).isEqualTo(2);
        assertThat(XMLParser.getDocumentAccessCount()).isEqualTo(2);
    }

    @Test
    void parsesDocumentThroughOracleV1AdapterLoadedInIsolatedClassLoader() throws Exception {
        IsolatedClassLoader classLoader = new IsolatedClassLoader(OracleV1DOMAdapterTest.class.getClassLoader());

        try {
            Callable<?> action = ServiceLoader.load(Callable.class, classLoader).stream()
                    .filter(provider -> provider.type().getName().equals(ISOLATED_CALLABLE_CLASS_NAME))
                    .map(ServiceLoader.Provider::get)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Expected isolated Oracle V1 DOM adapter callable provider"));

            assertThat(action.call()).isEqualTo("isolated-oracle-v1:1:1:1");
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    @Test
    void reportsSaxParseFailuresFromOracleV1ParserReflection() throws Exception {
        XMLParser.resetInvocationCounts();
        OracleV1DOMAdapter adapter = new OracleV1DOMAdapter();
        String xml = """
                <root xmlns="urn:jdom-oracle" xmlns:item="urn:jdom-oracle-item">
                    <item:child id="oracle-v1">value</item:child>
                </root>
                """;
        parse(adapter, xml);

        Executable parseMalformedXml = () -> parse(adapter, "<root><broken></root>");

        IOException exception = assertThrows(IOException.class, parseMalformedXml);
        assertThat(exception.getMessage()).contains("Error on line");
        assertThat(XMLParser.getInstanceCount()).isEqualTo(2);
        assertThat(XMLParser.getParseCount()).isEqualTo(2);
        assertThat(XMLParser.getDocumentAccessCount()).isEqualTo(1);
    }

    public static final class IsolatedOracleV1DOMAdapterCallable implements Callable<String> {
        @Override
        public String call() throws Exception {
            XMLParser.resetInvocationCounts();
            OracleV1DOMAdapter adapter = new OracleV1DOMAdapter();
            byte[] xmlBytes = "<root id=\"isolated-oracle-v1\" />".getBytes(StandardCharsets.UTF_8);
            Document document;
            try (InputStream inputStream = new ByteArrayInputStream(xmlBytes)) {
                document = adapter.getDocument(inputStream, false);
            }

            return document.getDocumentElement().getAttribute("id")
                    + ":" + XMLParser.getInstanceCount()
                    + ":" + XMLParser.getParseCount()
                    + ":" + XMLParser.getDocumentAccessCount();
        }
    }

    private static Document parse(OracleV1DOMAdapter adapter, String xml) throws IOException {
        byte[] xmlBytes = xml.getBytes(StandardCharsets.UTF_8);
        try (InputStream inputStream = new ByteArrayInputStream(xmlBytes)) {
            return adapter.getDocument(inputStream, false);
        }
    }

    private static void assertOracleV1Document(Document document, String expectedText) {
        Element root = document.getDocumentElement();
        Element child = (Element) root.getElementsByTagNameNS("urn:jdom-oracle-item", "child").item(0);
        assertThat(root.getNamespaceURI()).isEqualTo("urn:jdom-oracle");
        assertThat(child.getAttribute("id")).isEqualTo("oracle-v1");
        assertThat(child.getTextContent()).isEqualTo(expectedText);
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
                    || className.startsWith(ORACLE_PARSER_PACKAGE)
                    || className.equals(ISOLATED_CALLABLE_CLASS_NAME);
        }
    }
}
