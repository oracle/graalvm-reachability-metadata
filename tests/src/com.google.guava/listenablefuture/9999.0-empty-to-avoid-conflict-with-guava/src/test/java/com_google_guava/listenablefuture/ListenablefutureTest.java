/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_guava.listenablefuture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ListenablefutureTest {
    private static final String LISTENABLE_FUTURE_PACKAGE_RESOURCE = "com/google/common/util/concurrent/";
    private static final String LISTENABLE_FUTURE_CLASS_NAME = "com.google.common.util.concurrent.ListenableFuture";
    private static final String LISTENABLE_FUTURE_CLASS_RESOURCE =
            "com/google/common/util/concurrent/ListenableFuture.class";
    private static final String POM_PROPERTIES_RESOURCE =
            "META-INF/maven/com.google.guava/listenablefuture/pom.properties";
    private static final String POM_XML_RESOURCE =
            "META-INF/maven/com.google.guava/listenablefuture/pom.xml";

    @Test
    void placeholderDoesNotExposeTheStandaloneListenableFuturePackageLayout() {
        assertThat(classLoader().getResource(LISTENABLE_FUTURE_PACKAGE_RESOURCE)).isNull();
        assertThat(classLoader().getResource(LISTENABLE_FUTURE_CLASS_RESOURCE)).isNull();
        assertThat(classLoader().getResourceAsStream(LISTENABLE_FUTURE_CLASS_RESOURCE)).isNull();
    }

    @Test
    void placeholderDoesNotExposeTheStandaloneListenableFutureBytecodeThroughEnumerationLookups() throws IOException {
        assertThat(Collections.list(classLoader().getResources(LISTENABLE_FUTURE_PACKAGE_RESOURCE))).isEmpty();
        assertThat(Collections.list(classLoader().getResources(LISTENABLE_FUTURE_CLASS_RESOURCE))).isEmpty();
    }

    @Test
    void threadContextClassLoaderAlsoSeesNoStandaloneListenableFutureBytecode() throws IOException {
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();

        assertThat(contextClassLoader).isNotNull();
        assertThat(contextClassLoader.getResource(LISTENABLE_FUTURE_CLASS_RESOURCE)).isNull();
        assertThat(contextClassLoader.getResourceAsStream(LISTENABLE_FUTURE_CLASS_RESOURCE)).isNull();
        assertThat(Collections.list(contextClassLoader.getResources(LISTENABLE_FUTURE_CLASS_RESOURCE))).isEmpty();
    }

    @Test
    void placeholderCannotLoadTheStandaloneListenableFutureType() {
        assertThatThrownBy(() -> classLoader().loadClass(LISTENABLE_FUTURE_CLASS_NAME))
                .isInstanceOf(ClassNotFoundException.class)
                .hasMessageContaining(LISTENABLE_FUTURE_CLASS_NAME);
    }

    @Test
    void placeholderPublishesMavenMetadataThatIdentifiesTheArtifact() throws IOException {
        Properties pomProperties = loadPomProperties();
        String pomXml = loadUtf8Resource(POM_XML_RESOURCE);
        String publishedVersion = pomProperties.getProperty("version");

        assertThat(pomProperties)
                .containsEntry("groupId", "com.google.guava")
                .containsEntry("artifactId", "listenablefuture");
        assertThat(publishedVersion).isNotBlank();
        assertThat(pomXml)
                .contains("<artifactId>listenablefuture</artifactId>")
                .contains("<version>" + publishedVersion + "</version>")
                .contains("empty artifact");
    }

    @Test
    void placeholderPomDocumentsItsConflictAvoidanceRole() throws IOException {
        String pomXml = loadUtf8Resource(POM_XML_RESOURCE);

        assertThat(pomXml)
                .contains("<name>Guava ListenableFuture only</name>")
                .contains("If users want all of Guava")
                .contains("that empty artifact over the \"real\" listenablefuture")
                .contains("conflict with the copy of ListenableFuture in guava itself");
    }

    private static ClassLoader classLoader() {
        return ListenablefutureTest.class.getClassLoader();
    }

    private static Properties loadPomProperties() throws IOException {
        Properties properties = new Properties();

        try (InputStream inputStream = classLoader().getResourceAsStream(POM_PROPERTIES_RESOURCE)) {
            assertThat(inputStream).isNotNull();
            properties.load(inputStream);
        }

        return properties;
    }

    private static String loadUtf8Resource(String resourceName) throws IOException {
        try (InputStream inputStream = classLoader().getResourceAsStream(resourceName)) {
            assertThat(inputStream).isNotNull();
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
