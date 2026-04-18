/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_guava.listenablefuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class ListenablefutureTest {
    private static final String POM_PROPERTIES_RESOURCE =
            "META-INF/maven/com.google.guava/listenablefuture/pom.properties";
    private static final String POM_XML_RESOURCE = "META-INF/maven/com.google.guava/listenablefuture/pom.xml";
    private static final String LISTENABLE_FUTURE_TYPE = "com.google.common.util.concurrent.ListenableFuture";

    @Test
    void exposesExpectedMavenCoordinates() throws IOException {
        Assumptions.assumeFalse(isNativeImageRuntime());

        Properties pomProperties = loadPomProperties();
        String pomXml = readUniqueResource(POM_XML_RESOURCE);

        assertThat(pomProperties.getProperty("groupId")).isEqualTo("com.google.guava");
        assertThat(pomProperties.getProperty("artifactId")).isEqualTo("listenablefuture");
        assertThat(pomProperties.getProperty("version")).isNotBlank();

        assertThat(pomXml).contains("<artifactId>listenablefuture</artifactId>");
        assertThat(pomXml).contains("<groupId>com.google.guava</groupId>");
        assertThat(pomXml).contains("<version>" + pomProperties.getProperty("version") + "</version>");
    }

    @Test
    void pomDeclaresThisCoordinateAsAnEmptyConflictAvoidanceArtifact() throws IOException {
        Assumptions.assumeFalse(isNativeImageRuntime());

        String pomXml = normalizeWhitespace(readUniqueResource(POM_XML_RESOURCE));

        assertThat(pomXml).contains("<artifactId>guava-parent</artifactId>");
        assertThat(pomXml).contains("<name>Guava ListenableFuture only</name>");
        assertThat(pomXml).contains("An empty artifact");
        assertThat(pomXml).contains("ListenableFuture");
        assertThat(pomXml).contains("avoid");
        assertThat(pomXml).contains("conflict");
        assertThat(pomXml).doesNotContain("<dependencies>");
    }

    @Test
    void jarContainsOnlyMetadataResources() throws IOException {
        Assumptions.assumeFalse(isNativeImageRuntime());

        List<String> jarEntryNames = loadJarEntryNames(POM_XML_RESOURCE);

        assertThat(jarEntryNames)
                .contains("META-INF/MANIFEST.MF", POM_XML_RESOURCE, POM_PROPERTIES_RESOURCE)
                .allMatch(entryName -> entryName.equals("META-INF") || entryName.startsWith("META-INF/"))
                .noneMatch(entryName -> entryName.endsWith(".class"))
                .noneMatch(entryName -> entryName.startsWith("META-INF/services/"));
    }

    @Test
    void manifestKeepsTheArtifactRuntimeInert() throws IOException {
        Assumptions.assumeFalse(isNativeImageRuntime());

        Attributes mainAttributes = loadManifest().getMainAttributes();

        assertThat(mainAttributes.getValue("Manifest-Version")).isEqualTo("1.0");
        assertThat(mainAttributes.getValue("Created-By")).isNotBlank();
        assertThat(mainAttributes.getValue("Built-By")).isNotBlank();
        assertThat(mainAttributes.getValue("Build-Jdk")).isNotBlank();
        assertThat(mainAttributes.getValue("Main-Class")).isNull();
        assertThat(mainAttributes.getValue("Class-Path")).isNull();
        assertThat(mainAttributes.getValue("Automatic-Module-Name")).isNull();
        assertThat(mainAttributes.getValue("Premain-Class")).isNull();
        assertThat(mainAttributes.getValue("Agent-Class")).isNull();
        assertThat(mainAttributes.getValue("Launcher-Agent-Class")).isNull();
        assertThat(mainAttributes.getValue("Multi-Release")).isNull();
    }

    @Test
    void doesNotProvideTheStandaloneListenableFutureType() {
        ClassLoader classLoader = getClass().getClassLoader();

        assertThatExceptionOfType(ClassNotFoundException.class)
                .isThrownBy(() -> Class.forName(LISTENABLE_FUTURE_TYPE, false, classLoader));
    }

    private static Properties loadPomProperties() throws IOException {
        Properties pomProperties = new Properties();

        try (InputStream inputStream = openUniqueResource(POM_PROPERTIES_RESOURCE)) {
            pomProperties.load(inputStream);
        }

        return pomProperties;
    }

    private static String readUniqueResource(String resourcePath) throws IOException {
        try (InputStream inputStream = openUniqueResource(resourcePath)) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static InputStream openUniqueResource(String resourcePath) throws IOException {
        return findUniqueResource(resourcePath).openStream();
    }

    private static URL findUniqueResource(String resourcePath) throws IOException {
        ClassLoader classLoader = ListenablefutureTest.class.getClassLoader();
        List<URL> resourceUrls = Collections.list(classLoader.getResources(resourcePath));

        assertThat(resourceUrls).hasSize(1);

        return resourceUrls.get(0);
    }

    private static List<String> loadJarEntryNames(String resourcePath) throws IOException {
        JarURLConnection connection = (JarURLConnection) findUniqueResource(resourcePath).openConnection();

        try (JarFile jarFile = connection.getJarFile()) {
            return jarFile.stream().map(entry -> entry.getName()).toList();
        }
    }

    private static Manifest loadManifest() throws IOException {
        JarURLConnection connection = (JarURLConnection) findUniqueResource(POM_XML_RESOURCE).openConnection();

        try (JarFile jarFile = connection.getJarFile()) {
            return jarFile.getManifest();
        }
    }

    private static String normalizeWhitespace(String text) {
        return text.replaceAll("\\s+", " ").trim();
    }

    private static boolean isNativeImageRuntime() {
        return "runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"));
    }
}
