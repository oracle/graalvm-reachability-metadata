/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_cloud.google_cloud_storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.security.SecureClassLoader;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class StorageOptionsTest {
    private static final String STORAGE_OPTIONS_CLASS_NAME = "com.google.cloud.storage.StorageOptions";

    @Test
    void staticInitializerFallsBackToPackageRelativePomPropertiesResource() throws Exception {
        IsolatingStorageOptionsClassLoader classLoader = new IsolatingStorageOptionsClassLoader(
                StorageOptionsTest.class.getClassLoader());

        try {
            Class<?> storageOptionsClass = Class.forName(STORAGE_OPTIONS_CLASS_NAME, true, classLoader);

            assertThat(storageOptionsClass.getName()).isEqualTo(STORAGE_OPTIONS_CLASS_NAME);
            assertThat(classLoader.absolutePomPropertiesRequested()).isTrue();
            assertThat(classLoader.packageRelativePomPropertiesRequested()).isTrue();
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static final class IsolatingStorageOptionsClassLoader extends SecureClassLoader {
        private static final String STORAGE_PACKAGE_PREFIX = "com.google.cloud.storage.";
        private static final String STORAGE_RESOURCE_PREFIX = "com/google/cloud/storage/";
        private static final String CLASS_FILE_SUFFIX = ".class";
        private static final String ABSOLUTE_POM_PROPERTIES_RESOURCE =
                "META-INF/maven/com.google.cloud/google-cloud-storage/pom.properties";
        private static final String PACKAGE_RELATIVE_POM_PROPERTIES_RESOURCE =
                "com/google/cloud/storage/META-INF/maven/com.google.cloud/google-cloud-storage/pom.properties";
        private static final byte[] PACKAGE_RELATIVE_POM_PROPERTIES_BYTES =
                "version=fallback-resource\n".getBytes(StandardCharsets.UTF_8);

        private boolean absolutePomPropertiesRequested;
        private boolean packageRelativePomPropertiesRequested;

        IsolatingStorageOptionsClassLoader(ClassLoader parent) {
            super(parent);
        }

        boolean absolutePomPropertiesRequested() {
            return absolutePomPropertiesRequested;
        }

        boolean packageRelativePomPropertiesRequested() {
            return packageRelativePomPropertiesRequested;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!name.startsWith(STORAGE_PACKAGE_PREFIX)) {
                return super.loadClass(name, resolve);
            }

            synchronized (getClassLoadingLock(name)) {
                Class<?> loadedClass = findLoadedClass(name);
                if (loadedClass == null) {
                    loadedClass = findStorageClass(name);
                }
                if (resolve) {
                    resolveClass(loadedClass);
                }
                return loadedClass;
            }
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            if (ABSOLUTE_POM_PROPERTIES_RESOURCE.equals(name)) {
                absolutePomPropertiesRequested = true;
                return null;
            }
            if (PACKAGE_RELATIVE_POM_PROPERTIES_RESOURCE.equals(name)) {
                packageRelativePomPropertiesRequested = true;
                return new ByteArrayInputStream(PACKAGE_RELATIVE_POM_PROPERTIES_BYTES);
            }
            return super.getResourceAsStream(name);
        }

        private Class<?> findStorageClass(String name) throws ClassNotFoundException {
            String resourceName = name.replace('.', '/') + CLASS_FILE_SUFFIX;
            if (!resourceName.startsWith(STORAGE_RESOURCE_PREFIX)) {
                throw new ClassNotFoundException(name);
            }

            URL resourceUrl = getParent().getResource(resourceName);
            if (resourceUrl == null) {
                throw new ClassNotFoundException(name);
            }

            try (InputStream inputStream = resourceUrl.openStream()) {
                byte[] classBytes = inputStream.readAllBytes();
                CodeSource codeSource = new CodeSource(resourceUrl, (CodeSigner[]) null);
                return defineClass(name, classBytes, 0, classBytes.length, codeSource);
            } catch (IOException exception) {
                throw new ClassNotFoundException(name, exception);
            }
        }
    }
}
