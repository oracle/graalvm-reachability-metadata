/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness.tasks;

import com.fasterxml.jackson.databind.JsonNode;

import javax.lang.model.SourceVersion;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/// Decides which `resources` entries of a coordinate's metadata name resources the test project
/// provides, so that `splitTestOnlyMetadata` (§AR-test-harness.5) keeps them out of the shipped
/// metadata (§FS-repository-functional-spec.5.1).
final class TestResourceClassifier {
    private static final String CLASS_FILE_SUFFIX = ".class";
    private static final String PROPERTIES_SUFFIX = ".properties";
    private static final String GLOB_METACHARACTERS = "*?{[";
    /// A `ResourceBundle` locale suffix: `_<language>`, then optional script, country and variant.
    private static final Pattern LOCALE_SUFFIX = Pattern.compile("_[a-z]{2,3}(_[A-Za-z0-9]+)*");

    private final Set<String> testPackages;
    private final Set<String> testResources;
    private final List<String> allowedPackages;

    /// Classifies against the files under the test project's `src/test/resources`, its
    /// `testPackages`, and the coordinate's `allowedPackages`.
    TestResourceClassifier(Path testsDirectory, Set<String> testPackages, List<String> allowedPackages)
            throws IOException {
        this.testPackages = testPackages;
        this.testResources = discoverTestResources(testsDirectory);
        this.allowedPackages = allowedPackages;
    }

    /// Whether `value` is one of `packages` or a name inside one, matched by whole segments.
    static boolean referencesAnyPackage(String value, Collection<String> packages) {
        return packages.stream().anyMatch(packageName -> referencesPackage(value, packageName));
    }

    /// Whether the `resources` entry is a glob or a bundle that the test project provides.
    boolean isTestResource(JsonNode entry) {
        return globIsTestResource(entry) || bundleIsTestResource(entry);
    }

    /// A resource glob is a test resource when it names a file under the test project's
    /// `src/test/resources` or matches only resources in a test package
    /// (§FS-repository-functional-spec.5.1).
    private boolean globIsTestResource(JsonNode entry) {
        JsonNode globNode = entry.isObject() ? entry.get("glob") : null;
        if (globNode == null || !globNode.isTextual()) {
            return false;
        }
        String resourcePath = normalizeResourcePath(globNode.asText());
        return testResources.contains(resourcePath) || isTestPackageResourceGlob(resourcePath);
    }

    /// A resource bundle is a test resource when the test project's `src/test/resources` provides
    /// its base `.properties` file or a `_<locale>` variant of it, or when it names a test-package
    /// class, a `ResourceBundle` subclass the tests define. Like a default-package type, a bundle
    /// class in the default package never moves; only a `.properties` file can move such a bundle.
    /// A bundle nothing provides stays shipped (§FS-repository-functional-spec.5.1).
    private boolean bundleIsTestResource(JsonNode entry) {
        JsonNode bundleNode = entry.isObject() ? entry.get("bundle") : null;
        if (bundleNode == null || !bundleNode.isTextual()) {
            return false;
        }
        String basePath = bundleNode.asText().replace('.', '/');
        String bundlePackage = basePath.substring(0, Math.max(basePath.lastIndexOf('/'), 0)).replace('/', '.');
        // A library's own bundle stays even when a test translates it or sits in its package
        // (§FS-test-contract.2.2).
        if (referencesAnyPackage(bundlePackage, allowedPackages)) {
            return false;
        }
        // A `ResourceBundle` subclass the tests define has its class file in a test package.
        return testResources.stream().anyMatch(resource -> isBundlePropertiesFile(resource, basePath))
                || isTestPackageResourceGlob(basePath + CLASS_FILE_SUFFIX);
    }

    /// Whether `resource` is `<basePath>.properties` or a `<basePath>_<locale>.properties` variant.
    private boolean isBundlePropertiesFile(String resource, String basePath) {
        if (!resource.startsWith(basePath) || !resource.endsWith(PROPERTIES_SUFFIX)) {
            return false;
        }
        String suffix = resource.substring(basePath.length(), resource.length() - PROPERTIES_SUFFIX.length());
        return suffix.isEmpty() || LOCALE_SUFFIX.matcher(suffix).matches();
    }

    /// Whether the glob `resourcePath` can match only resources in a test package, whatever the
    /// file name: class bytes a library reads (Guice's `$LineNumbers`) or the package directory a
    /// classpath scan looks up (Spring's `EntityScanner`). Its literal directories, up to the
    /// first segment with a glob metacharacter, must be identifiers naming a test package or a
    /// package under one. In a fully literal glob, a last segment that is an identifier counts as
    /// a directory, so the bare package directory matches too.
    private boolean isTestPackageResourceGlob(String resourcePath) {
        String[] segments = resourcePath.split("/", -1);
        int last = segments.length - 1;
        List<String> packageSegments = new ArrayList<>();
        for (int i = 0; i < last && !hasGlobMetacharacter(segments[i]); i++) {
            if (!SourceVersion.isIdentifier(segments[i])) {
                return false;
            }
            packageSegments.add(segments[i]);
        }
        if (packageSegments.size() == last && SourceVersion.isIdentifier(segments[last])) {
            packageSegments.add(segments[last]);
        }
        if (packageSegments.isEmpty()) {
            return false;
        }
        String packageName = String.join(".", packageSegments);
        // A test misplaced in a library package (§FS-test-contract.2.2) leaves its files shipped.
        return referencesAnyPackage(packageName, testPackages)
                && !referencesAnyPackage(packageName, allowedPackages);
    }

    private static boolean hasGlobMetacharacter(String segment) {
        return segment.chars().anyMatch(character -> GLOB_METACHARACTERS.indexOf(character) >= 0);
    }

    private static boolean referencesPackage(String value, String packageName) {
        return value.equals(packageName) || value.startsWith(packageName + ".");
    }

    private static Set<String> discoverTestResources(Path testsDirectory) throws IOException {
        Set<String> resources = new LinkedHashSet<>();
        Path resourceRoot = testsDirectory.resolve("src/test/resources");
        if (!Files.isDirectory(resourceRoot)) {
            return resources;
        }
        try (var pathStream = Files.walk(resourceRoot)) {
            pathStream.filter(Files::isRegularFile)
                    .map(resourceRoot::relativize)
                    .map(Path::toString)
                    .map(TestResourceClassifier::normalizeResourcePath)
                    .forEach(resources::add);
        }

        return resources;
    }

    private static String normalizeResourcePath(String path) {
        String normalized = path.replace('\\', '/');
        if (normalized.startsWith("/")) {
            return normalized.substring(1);
        }
        return normalized;
    }
}
