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
import org.jdom.adapters.OracleV2DOMAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class OracleV2DOMAdapterTest {
    private static final String ISOLATED_CALLABLE_CLASS_NAME =
            OracleV2DOMAdapterTest.class.getName() + "$IsolatedOracleV2DOMAdapterCallable";
    private static final String JDOM_PACKAGE = "org.jdom.";
    private static final String ORACLE_PARSER_PACKAGE = "oracle.xml.parser.";

    @Test
    void parsesNamespaceAwareDocumentThroughOracleV2ParserReflection() throws Exception {
        XMLParser.resetInvocationCounts();
        OracleV2DOMAdapter adapter = new OracleV2DOMAdapter();
        String xml = """
                <catalog xmlns="urn:jdom-oracle-v2">
                    <entry sku="v2">Oracle V2</entry>
                </catalog>
                """;

        Document document = parse(adapter, xml);
        Document secondDocument = parse(adapter, xml);

        assertOracleV2Document(document, "Oracle V2");
        assertOracleV2Document(secondDocument, "Oracle V2");
        assertThat(XMLParser.getInstanceCount()).isEqualTo(2);
        assertThat(XMLParser.getParseCount()).isEqualTo(2);
        assertThat(XMLParser.getDocumentAccessCount()).isEqualTo(2);
    }

    @Test
    void parsesDocumentThroughOracleV2AdapterLoadedInIsolatedClassLoader() throws Exception {
        IsolatedClassLoader classLoader = new IsolatedClassLoader(OracleV2DOMAdapterTest.class.getClassLoader());

        try {
            Callable<?> action = ServiceLoader.load(Callable.class, classLoader).stream()
                    .filter(provider -> provider.type().getName().equals(ISOLATED_CALLABLE_CLASS_NAME))
                    .map(ServiceLoader.Provider::get)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Expected isolated Oracle V2 DOM adapter callable provider"));

            assertThat(action.call()).isEqualTo("isolated-oracle-v2:1:1:1");
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    @Test
    void reportsSaxParseFailuresFromOracleV2ParserReflection() throws Exception {
        XMLParser.resetInvocationCounts();
        OracleV2DOMAdapter adapter = new OracleV2DOMAdapter();
        parse(adapter, "<catalog><entry>Oracle V2</entry></catalog>");

        Executable parseMalformedXml = () -> parse(adapter, "<catalog><entry></catalog>");

        IOException exception = assertThrows(IOException.class, parseMalformedXml);
        assertThat(exception.getMessage()).contains("Error on line");
        assertThat(XMLParser.getInstanceCount()).isEqualTo(2);
        assertThat(XMLParser.getParseCount()).isEqualTo(2);
        assertThat(XMLParser.getDocumentAccessCount()).isEqualTo(1);
    }

    public static final class IsolatedOracleV2DOMAdapterCallable implements Callable<String> {
        @Override
        public String call() throws Exception {
            XMLParser.resetInvocationCounts();
            OracleV2DOMAdapter adapter = new OracleV2DOMAdapter();
            byte[] xmlBytes = "<catalog id=\"isolated-oracle-v2\" />".getBytes(StandardCharsets.UTF_8);
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

    private static Document parse(OracleV2DOMAdapter adapter, String xml) throws IOException {
        byte[] xmlBytes = xml.getBytes(StandardCharsets.UTF_8);
        try (InputStream inputStream = new ByteArrayInputStream(xmlBytes)) {
            return adapter.getDocument(inputStream, false);
        }
    }

    private static void assertOracleV2Document(Document document, String expectedText) {
        Element root = document.getDocumentElement();
        Element entry = (Element) root.getElementsByTagNameNS("urn:jdom-oracle-v2", "entry").item(0);
        assertThat(root.getNamespaceURI()).isEqualTo("urn:jdom-oracle-v2");
        assertThat(entry.getAttribute("sku")).isEqualTo("v2");
        assertThat(entry.getTextContent()).isEqualTo(expectedText);
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
