/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_fusesource_jansi.jansi;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.fusesource.hawtjni.runtime.Library;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class LibraryTest {

    private static final String MISSING_LIBRARY_NAME = "graalvmjansimissingnative";
    private static final String EXTRACTED_LIBRARY_NAME = "graalvmjansiextractnative";
    private static final String TEST_LIBRARY_VERSION = "integration-test";

    @Test
    void loadChecksBundledNativeLibraryResourcesWhenNativeLibraryLookupFails() {
        TrackingResourceClassLoader classLoader = new TrackingResourceClassLoader(LibraryTest.class.getClassLoader());
        Library library = new Library(MISSING_LIBRARY_NAME, TEST_LIBRARY_VERSION, classLoader);

        assertThatThrownBy(library::load)
                .isInstanceOf(UnsatisfiedLinkError.class)
                .hasMessageContaining("Could not load library. Reasons:");

        assertThat(classLoader.requestedResources).containsExactlyElementsOf(expectedResourceLookups(library));
    }

    @Test
    void loadMakesExtractedNativeLibraryExecutableBeforeLoading(@TempDir final Path tempDirectory) throws IOException {
        Path extractionDirectory = tempDirectory.resolve("extracted");
        Files.createDirectories(extractionDirectory);
        Path userHomeFile = tempDirectory.resolve("user-home-file");
        Files.write(userHomeFile, new byte[0]);

        Library directoryProbe = new Library(EXTRACTED_LIBRARY_NAME, TEST_LIBRARY_VERSION, null);
        String versionedLibraryName = EXTRACTED_LIBRARY_NAME + "-" + TEST_LIBRARY_VERSION;
        String resourceName = resourcePath(directoryProbe.getSpecificSearchDirs()[0], versionedLibraryName);
        Path resourceFile = tempDirectory.resolve(mapLibraryName(versionedLibraryName));
        Files.write(resourceFile, "not a native library".getBytes(StandardCharsets.UTF_8));
        TrackingResourceClassLoader classLoader = new TrackingResourceClassLoader(
                LibraryTest.class.getClassLoader(),
                resourceName,
                resourceFile.toUri().toURL());
        Library library = new Library(EXTRACTED_LIBRARY_NAME, TEST_LIBRARY_VERSION, classLoader);

        String previousTempDirectory = setSystemProperty("java.io.tmpdir", extractionDirectory.toString());
        String previousUserHome = setSystemProperty("user.home", userHomeFile.toString());
        try {
            assertThatThrownBy(library::load)
                    .isInstanceOf(UnsatisfiedLinkError.class)
                    .hasMessageContaining("Could not load library. Reasons:");
        } finally {
            restoreSystemProperty("java.io.tmpdir", previousTempDirectory);
            restoreSystemProperty("user.home", previousUserHome);
        }

        assertThat(classLoader.requestedResources).contains(resourceName);
        List<Path> extractedLibraries = extractedLibraries(extractionDirectory, versionedLibraryName);
        assertThat(extractedLibraries).hasSize(1);
        if (!Library.getPlatform().startsWith("windows")) {
            assertThat(Files.isExecutable(extractedLibraries.get(0))).isTrue();
        }
    }

    private static List<String> expectedResourceLookups(final Library library) {
        List<String> paths = new ArrayList<String>();
        for (String directory : library.getSpecificSearchDirs()) {
            paths.add(resourcePath(directory, MISSING_LIBRARY_NAME + "-" + TEST_LIBRARY_VERSION));
            paths.add(resourcePath(directory, MISSING_LIBRARY_NAME));
        }
        return paths;
    }

    private static String resourcePath(final String directory, final String libraryName) {
        return "META-INF/native/" + directory + "/" + mapLibraryName(libraryName);
    }

    private static String mapLibraryName(final String libraryName) {
        String mappedName = System.mapLibraryName(libraryName);
        if (mappedName.endsWith(".dylib")) {
            return mappedName.substring(0, mappedName.length() - ".dylib".length()) + ".jnilib";
        }
        return mappedName;
    }

    private static List<Path> extractedLibraries(final Path directory, final String libraryName) throws IOException {
        String mappedName = mapLibraryName(libraryName);
        int extensionStart = mappedName.lastIndexOf('.');
        String prefix = mappedName.substring(0, extensionStart) + "-";
        String suffix = mappedName.substring(extensionStart);
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(path -> extractedLibraryNameMatches(path, prefix, suffix)).collect(Collectors.toList());
        }
    }

    private static boolean extractedLibraryNameMatches(final Path path, final String prefix, final String suffix) {
        String fileName = path.getFileName().toString();
        return fileName.startsWith(prefix) && fileName.endsWith(suffix);
    }

    private static String setSystemProperty(final String key, final String value) {
        String previousValue = System.getProperty(key);
        System.setProperty(key, value);
        return previousValue;
    }

    private static void restoreSystemProperty(final String key, final String previousValue) {
        if (previousValue == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, previousValue);
        }
    }

    public static final class TrackingResourceClassLoader extends ClassLoader {

        private final List<String> requestedResources = new ArrayList<String>();
        private final String resourceName;
        private final URL resource;

        public TrackingResourceClassLoader(final ClassLoader parent) {
            this(parent, null, null);
        }

        public TrackingResourceClassLoader(final ClassLoader parent, final String resourceName, final URL resource) {
            super(parent);
            this.resourceName = resourceName;
            this.resource = resource;
        }

        @Override
        public URL getResource(final String name) {
            requestedResources.add(name);
            if (name.equals(resourceName)) {
                return resource;
            }
            return null;
        }
    }
}
