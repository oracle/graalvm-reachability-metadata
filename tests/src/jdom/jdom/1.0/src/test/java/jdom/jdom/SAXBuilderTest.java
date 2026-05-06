/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jdom.jdom;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.ServiceLoader;
import java.util.concurrent.Callable;

import org.graalvm.internal.tck.NativeImageSupport;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.Namespace;
import org.jdom.input.SAXBuilder;
import org.junit.jupiter.api.Test;

public class SAXBuilderTest {
    private static final String ISOLATED_CALLABLE_CLASS_NAME =
            SAXBuilderTest.class.getName() + "$IsolatedSAXBuilderCallable";
    private static final String JDOM_PACKAGE = "org.jdom.";

    @Test
    void buildsNamespaceAwareDocumentWithDefaultJaxpParser() throws Exception {
        SAXBuilder builder = new SAXBuilder();
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <catalog xmlns="urn:jdom:catalog" xmlns:item="urn:jdom:item" id="catalog-1">
                    <item:book code="native-image">
                        <item:title>GraalVM Native Image</item:title>
                    </item:book>
                </catalog>
                """;

        Document document = builder.build(new StringReader(xml));

        Namespace catalogNamespace = Namespace.getNamespace("urn:jdom:catalog");
        Namespace itemNamespace = Namespace.getNamespace("item", "urn:jdom:item");
        Element root = document.getRootElement();
        Element book = root.getChild("book", itemNamespace);
        Element title = book.getChild("title", itemNamespace);

        assertThat(root.getName()).isEqualTo("catalog");
        assertThat(root.getNamespace()).isEqualTo(catalogNamespace);
        assertThat(root.getAttributeValue("id")).isEqualTo("catalog-1");
        assertThat(book.getAttributeValue("code")).isEqualTo("native-image");
        assertThat(title.getTextNormalize()).isEqualTo("GraalVM Native Image");
    }

    @Test
    void buildsDocumentWithDefaultParserFromIsolatedJdomClasses() throws Exception {
        IsolatedClassLoader classLoader = new IsolatedClassLoader(SAXBuilderTest.class.getClassLoader());

        try {
            Callable<?> action = ServiceLoader.load(Callable.class, classLoader).stream()
                    .map(ServiceLoader.Provider::get)
                    .filter(provider -> provider.getClass().getName().equals(ISOLATED_CALLABLE_CLASS_NAME))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Expected isolated SAXBuilder callable provider"));

            assertThat(action.call()).isEqualTo("isolated-parser");
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    public static final class IsolatedSAXBuilderCallable implements Callable<String> {
        @Override
        public String call() throws Exception {
            SAXBuilder builder = new SAXBuilder();
            String xml = """
                    <root id="isolated-parser" />
                    """;

            Document document = builder.build(new StringReader(xml));

            return document.getRootElement().getAttributeValue("id");
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
            return className.startsWith(JDOM_PACKAGE) || className.equals(ISOLATED_CALLABLE_CLASS_NAME);
        }
    }
}
