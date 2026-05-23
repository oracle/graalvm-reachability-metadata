/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_poi.poi_ooxml_schemas;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.schemas.office.x2006.encryption.CTEncryption;
import com.microsoft.schemas.office.x2006.encryption.CTKeyData;
import com.microsoft.schemas.office.x2006.encryption.EncryptionDocument;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;
import org.apache.xmlbeans.SchemaTypeSystem;
import org.apache.xmlbeans.XmlBeans;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class SchemaorgApacheXmlbeansSystemS8C3F193EE11A2F798ACF65489B9E6078TypeSystemHolderTest {
    private static final String TYPE_SYSTEM_NAME =
            "schemaorg_apache_xmlbeans.system.s8C3F193EE11A2F798ACF65489B9E6078";

    @Test
    void loadsTypeSystemFromClassLoaderAndCreatesEncryptionSchemaDocument() throws Exception {
        try (ChildFirstTypeSystemClassLoader loader = new ChildFirstTypeSystemClassLoader(
                new URL[] {schemaJarUrl()}, getClass().getClassLoader())) {
            SchemaTypeSystem typeSystem = XmlBeans.typeSystemForClassLoader(loader, TYPE_SYSTEM_NAME);
            EncryptionDocument document = EncryptionDocument.Factory.newInstance();

            assertThat(typeSystem).isNotNull();
            assertThat(typeSystem.resolveHandle("encryptione8b3doctype")).isNotNull();

            CTEncryption encryption = document.addNewEncryption();
            CTKeyData keyData = encryption.addNewKeyData();

            assertThat(document.getEncryption()).isNotNull();
            assertThat(encryption.getKeyData()).isNotNull();
            assertThat(keyData).isNotNull();
            assertThat(document.xmlText()).contains("encryption", "keyData");
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static URL schemaJarUrl() {
        CodeSource codeSource = EncryptionDocument.class.getProtectionDomain().getCodeSource();
        assertThat(codeSource).isNotNull();
        URL location = codeSource.getLocation();
        assertThat(location).isNotNull();
        return location;
    }

    private static final class ChildFirstTypeSystemClassLoader extends URLClassLoader {
        ChildFirstTypeSystemClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> loadedClass = findLoadedClass(name);
                if (loadedClass == null && isTypeSystemClass(name)) {
                    try {
                        loadedClass = findClass(name);
                    } catch (ClassNotFoundException ignored) {
                        loadedClass = null;
                    }
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

        private static boolean isTypeSystemClass(String name) {
            return name.startsWith(TYPE_SYSTEM_NAME + ".");
        }
    }
}
