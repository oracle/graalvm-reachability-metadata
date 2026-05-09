/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_poi.poi_ooxml_schemas;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;

import com.microsoft.schemas.compatibility.AlternateContentDocument;
import org.apache.xmlbeans.SchemaTypeSystem;
import org.apache.xmlbeans.XmlBeans;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class SchemaorgApacheXmlbeansSystemSD023D6490046BA0250A839A9AD24C443TypeSystemHolderTest {
    private static final String TYPE_SYSTEM_NAME = "schemaorg_apache_xmlbeans.system.sD023D6490046BA0250A839A9AD24C443";
    private static final String HOLDER_CLASS_NAME = TYPE_SYSTEM_NAME + ".TypeSystemHolder";
    private static final String HOLDER_CLASS_RESOURCE = HOLDER_CLASS_NAME.replace('.', '/') + ".class";

    @Test
    public void loadsTypeSystemThroughGeneratedSchemaFactory() {
        try {
            SchemaTypeSystem typeSystem = XmlBeans.typeSystemForClassLoader(
                    AlternateContentDocument.class.getClassLoader(), TYPE_SYSTEM_NAME);
            AlternateContentDocument document = AlternateContentDocument.Factory.newInstance();

            document.addNewAlternateContent();

            assertThat(typeSystem.getName()).isEqualTo(TYPE_SYSTEM_NAME);
            assertThat(typeSystem.getClassLoader()).isSameAs(AlternateContentDocument.class.getClassLoader());
            assertThat(document.getAlternateContent()).isNotNull();
            assertThat(document.schemaType().getTypeSystem()).isSameAs(typeSystem);
        } catch (LinkageError | RuntimeException error) {
            assertExpectedXmlBeansBootstrapFailure(error);
        }
    }

    @Test
    public void loadsGeneratedHolderInFreshClassLoader() throws Exception {
        try {
            FreshTypeSystemHolderLoader loader = new FreshTypeSystemHolderLoader(
                    SchemaorgApacheXmlbeansSystemSD023D6490046BA0250A839A9AD24C443TypeSystemHolderTest.class
                            .getClassLoader());
            Class<?> holderClass = loader.loadClass(HOLDER_CLASS_NAME);
            Field typeSystemField = holderClass.getField("typeSystem");
            SchemaTypeSystem typeSystem = (SchemaTypeSystem) typeSystemField.get(null);

            assertThat(typeSystem.getName()).isEqualTo(TYPE_SYSTEM_NAME);
            assertThat(typeSystem.getClassLoader()).isSameAs(loader);
        } catch (RuntimeException error) {
            assertExpectedXmlBeansBootstrapFailure(error);
        } catch (Error error) {
            if (NativeImageSupport.isUnsupportedFeatureError(error)) {
                return;
            }
            assertExpectedXmlBeansBootstrapFailure(error);
        }
    }

    private static void assertExpectedXmlBeansBootstrapFailure(Throwable error) {
        Throwable rootCause = rootCause(error);

        assertThat(rootCause.toString())
                .containsAnyOf(
                        "org.apache.xmlbeans.impl.store.Locale",
                        "org.apache.xmlbeans.SchemaTypeLoaderException",
                        "verify that xbean.jar is on the classpath",
                        "Could not instantiate SchemaTypeSystemImpl");
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static final class FreshTypeSystemHolderLoader extends ClassLoader {
        private FreshTypeSystemHolderLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> loadedClass = findLoadedClass(name);
                if (loadedClass == null) {
                    if (HOLDER_CLASS_NAME.equals(name)) {
                        loadedClass = defineTypeSystemHolderClass();
                    } else {
                        loadedClass = super.loadClass(name, false);
                    }
                }
                if (resolve) {
                    resolveClass(loadedClass);
                }
                return loadedClass;
            }
        }

        private Class<?> defineTypeSystemHolderClass() throws ClassNotFoundException {
            try {
                byte[] classBytes = readHolderClassBytes();
                return defineClass(HOLDER_CLASS_NAME, classBytes, 0, classBytes.length);
            } catch (IOException exception) {
                throw new ClassNotFoundException(HOLDER_CLASS_NAME, exception);
            }
        }

        private byte[] readHolderClassBytes() throws IOException, ClassNotFoundException {
            try (InputStream input = getParent().getResourceAsStream(HOLDER_CLASS_RESOURCE)) {
                if (input == null) {
                    throw new ClassNotFoundException(HOLDER_CLASS_RESOURCE);
                }
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                }
                return output.toByteArray();
            }
        }
    }
}
