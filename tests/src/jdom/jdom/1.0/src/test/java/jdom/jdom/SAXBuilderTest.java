/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jdom.jdom;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.Reader;
import java.io.StringReader;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;

import org.graalvm.internal.tck.NativeImageSupport;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.Namespace;
import org.jdom.input.SAXBuilder;
import org.junit.jupiter.api.Test;

public class SAXBuilderTest {
    @Test
    void buildWithDefaultJaxpParserReadsNamespaceAwareXml() throws Exception {
        SAXBuilder builder = new SAXBuilder();
        String xml = """
                <sample:root xmlns:sample="urn:jdom-sax-builder-test" sample:id="parsed">
                    <sample:child>content</sample:child>
                </sample:root>
                """;

        Document document = builder.build(new StringReader(xml));

        Namespace namespace = Namespace.getNamespace("sample", "urn:jdom-sax-builder-test");
        Element root = document.getRootElement();
        Element child = root.getChild("child", namespace);
        assertThat(root.getName()).isEqualTo("root");
        assertThat(root.getNamespaceURI()).isEqualTo("urn:jdom-sax-builder-test");
        assertThat(root.getAttributeValue("id", namespace)).isEqualTo("parsed");
        assertThat(child.getText()).isEqualTo("content");
    }

    @Test
    void freshLoadedBuilderUsesDefaultJaxpParser() throws Exception {
        try {
            URL jdomJar = SAXBuilder.class.getProtectionDomain().getCodeSource().getLocation();
            try (ChildFirstJdomClassLoader classLoader = new ChildFirstJdomClassLoader(new URL[] {jdomJar })) {
                Class<?> builderClass = Class.forName(SAXBuilder.class.getName(), true, classLoader);
                Object builder = builderClass.getConstructor().newInstance();
                Method build = builderClass.getMethod("build", Reader.class);
                Object document = build.invoke(builder, new StringReader("<root><child>fresh</child></root>"));

                Method getRootElement = document.getClass().getMethod("getRootElement");
                Object root = getRootElement.invoke(document);
                Method getName = root.getClass().getMethod("getName");
                assertThat(getName.invoke(root)).isEqualTo("root");
            }
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    public static class ChildFirstJdomClassLoader extends URLClassLoader {
        public ChildFirstJdomClassLoader(URL[] urls) {
            super(urls, SAXBuilderTest.class.getClassLoader());
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> loadedClass = findLoadedClass(name);
                if (loadedClass == null && name.startsWith("org.jdom.")) {
                    try {
                        loadedClass = findClass(name);
                    } catch (ClassNotFoundException ignored) {
                        loadedClass = super.loadClass(name, false);
                    }
                } else if (loadedClass == null) {
                    loadedClass = super.loadClass(name, false);
                }
                if (resolve) {
                    resolveClass(loadedClass);
                }
                return loadedClass;
            }
        }
    }
}
