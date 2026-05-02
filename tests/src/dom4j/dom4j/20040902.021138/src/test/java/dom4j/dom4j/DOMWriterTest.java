/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dom4j.dom4j;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.dom.DOMDocument;
import org.dom4j.io.DOMWriter;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class DOMWriterTest {
    @Test
    void resolvesClassLiteralHelperInFreshDomWriterDefinition() throws Exception {
        try {
            ClassLoader classLoader = new IsolatedDomWriterClassLoader(
                    DOMWriter.class.getClassLoader());
            Class<?> isolatedWriterClass = classLoader.loadClass(DOMWriter.class.getName());
            Object writer = isolatedWriterClass.getConstructor().newInstance();

            isolatedWriterClass.getMethod("setDomDocumentClassName", String.class)
                    .invoke(writer, DOMDocument.class.getName());
            Object resolvedClass = isolatedWriterClass.getMethod("getDomDocumentClass")
                    .invoke(writer);

            assertThat(resolvedClass).isEqualTo(DOMDocument.class);
        } catch (InvocationTargetException exception) {
            if (isUnsupportedFeatureError(exception.getCause())) {
                return;
            }
            throw exception;
        } catch (Error error) {
            if (NativeImageSupport.isUnsupportedFeatureError(error)) {
                return;
            }
            throw error;
        }
    }

    @Test
    void resolvesAndInstantiatesDomDocuments() throws Exception {
        DOMWriter defaultWriter = new DOMWriter();

        Class<?> defaultDocumentClass = defaultWriter.getDomDocumentClass();

        assertThat(org.w3c.dom.Document.class.isAssignableFrom(defaultDocumentClass))
                .isTrue();

        DOMWriter namedWriter = new DOMWriter();
        namedWriter.setDomDocumentClassName(DOMDocument.class.getName());
        assertThat(namedWriter.getDomDocumentClass()).isEqualTo(DOMDocument.class);

        org.w3c.dom.Document configuredDocument = new DOMWriter(RecordingDomDocument.class)
                .write(sampleDocument());
        assertDomDocument(configuredDocument, RecordingDomDocument.class);

        org.w3c.dom.Document fallbackDocument = new FallbackDOMWriter().write(sampleDocument());
        assertDomDocument(fallbackDocument, RecordingDomDocument.class);
    }

    private static boolean isUnsupportedFeatureError(Throwable throwable) {
        return throwable instanceof Error
                && NativeImageSupport.isUnsupportedFeatureError((Error) throwable);
    }

    private static Document sampleDocument() {
        Element root = DocumentHelper.createElement("root");
        root.addAttribute("id", "sample");
        root.addElement("child").addText("value");

        return DocumentHelper.createDocument(root);
    }

    private static void assertDomDocument(org.w3c.dom.Document document,
            Class<? extends org.w3c.dom.Document> expectedClass) {
        assertThat(document).isInstanceOf(expectedClass);
        assertThat(document.getDocumentElement().getNodeName()).isEqualTo("root");
        assertThat(document.getDocumentElement().getAttribute("id"))
                .isEqualTo("sample");
        assertThat(document.getDocumentElement().getFirstChild().getNodeName())
                .isEqualTo("child");
    }

    public static final class RecordingDomDocument extends DOMDocument {
        public RecordingDomDocument() {
            super();
        }
    }

    private static final class IsolatedDomWriterClassLoader extends ClassLoader {
        private static final String DOM_WRITER_CLASS_NAME = "org.dom4j.io.DOMWriter";
        private static final String DOM_WRITER_RESOURCE_NAME = "org/dom4j/io/DOMWriter.class";

        private IsolatedDomWriterClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve)
                throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> loadedClass = findLoadedClass(name);
                if (loadedClass == null && DOM_WRITER_CLASS_NAME.equals(name)) {
                    loadedClass = findClass(name);
                }
                if (loadedClass == null) {
                    loadedClass = super.loadClass(name, false);
                }
                if (resolve) {
                    resolveClass(loadedClass);
                }
                return loadedClass;
            }
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (!DOM_WRITER_CLASS_NAME.equals(name)) {
                return super.findClass(name);
            }

            try (InputStream input = getParent().getResourceAsStream(
                    DOM_WRITER_RESOURCE_NAME)) {
                if (input == null) {
                    throw new ClassNotFoundException(name);
                }
                byte[] classBytes = input.readAllBytes();
                return defineClass(name, classBytes, 0, classBytes.length);
            } catch (IOException exception) {
                throw new ClassNotFoundException(name, exception);
            }
        }
    }

    private static final class FallbackDOMWriter extends DOMWriter {
        @Override
        public Class getDomDocumentClass() {
            return RecordingDomDocument.class;
        }

        @Override
        protected org.w3c.dom.Document createDomDocumentViaJAXP() throws DocumentException {
            return null;
        }
    }
}
