/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.utils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Utilities for inspecting JAR files to derive package structures.
 */
public abstract class JarUtils {

    private static final String CLASS_SUFFIX = ".class";
    private static final String MODULE_INFO = "module-info.class";
    private static final String META_INF_VERSIONS = "META-INF/versions/";
    private static final Pattern VERSIONED_ENTRY = Pattern.compile("META-INF/versions/([1-9][0-9]{0,8})/(.+)");

    /**
     * Returns whether a resolved artifact is the main JAR eligible for the coverage
     * denominator: the unclassified one, or the one classified as the suffix by which
     * the requested version names a published variant. §AR-test-harness.8
     */
    public static boolean isMainJar(String extension, String classifier, String requestedVersion, String resolvedVersion) {
        if (!"jar".equals(extension)) {
            return false;
        }
        if (classifier == null || classifier.isEmpty()) {
            return true;
        }
        return requestedVersion.equals(resolvedVersion + "-" + classifier);
    }

    /// Extracts into `outputDir` the classes a JVM of release `runtimeMajor` loads from `jar`, one
    /// file per class: the base classes, then each `META-INF/versions/<v>/` overlay with
    /// `v <= runtimeMajor` in ascending order and its prefix stripped, so the newest one wins.
    ///
    /// Entries are read with `java.util.zip` and their timestamps are ignored, so a JAR stamped
    /// before 1970, which Gradle's `zipTree` copy rejects, extracts like any other.
    /// §AR-test-harness.8
    public static void extractEffectiveClasses(Path jar, int runtimeMajor, Path outputDir) throws IOException {
        Path root = outputDir.toAbsolutePath().normalize();
        Files.createDirectories(root);
        try (ZipFile zipFile = new ZipFile(jar.toFile())) {
            Map<String, ZipEntry> baseClasses = new TreeMap<>();
            Map<Integer, Map<String, ZipEntry>> overlays = new TreeMap<>();
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory() || !name.endsWith(CLASS_SUFFIX)) {
                    continue;
                }
                if (!name.startsWith(META_INF_VERSIONS)) {
                    baseClasses.put(name, entry);
                    continue;
                }
                Matcher versioned = VERSIONED_ENTRY.matcher(name);
                if (!versioned.matches()) {
                    continue;
                }
                int version = Integer.parseInt(versioned.group(1));
                if (version <= runtimeMajor) {
                    overlays.computeIfAbsent(version, v -> new TreeMap<>()).put(versioned.group(2), entry);
                }
            }

            Map<String, ZipEntry> effectiveClasses = new TreeMap<>(baseClasses);
            overlays.values().forEach(effectiveClasses::putAll);
            for (Map.Entry<String, ZipEntry> effectiveClass : effectiveClasses.entrySet()) {
                Path target = root.resolve(effectiveClass.getKey()).normalize();
                if (!target.startsWith(root)) {
                    throw new IOException("Entry '" + effectiveClass.getValue().getName() + "' of " + jar
                            + " resolves outside " + root);
                }
                Files.createDirectories(target.getParent());
                try (InputStream input = zipFile.getInputStream(effectiveClass.getValue())) {
                    Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    /**
     * Scans the given JARs and returns all fully-qualified class names found.
     * Handles multi-release JARs by stripping the {@code META-INF/versions/N/} prefix.
     * Excludes {@code module-info.class}.
     */
    public static Set<String> loadClassNames(List<Path> jars) throws IOException {
        Set<String> classNames = new HashSet<>();
        for (Path jar : jars) {
            try (JarFile jarFile = new JarFile(jar.toFile())) {
                Enumeration<JarEntry> entries = jarFile.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    if (entry.isDirectory()) {
                        continue;
                    }
                    String name = entry.getName();

                    // Strip multi-release prefix
                    if (name.startsWith(META_INF_VERSIONS)) {
                        int thirdSlash = name.indexOf('/', META_INF_VERSIONS.length());
                        if (thirdSlash >= 0) {
                            name = name.substring(thirdSlash + 1);
                        } else {
                            continue;
                        }
                    }

                    if (!name.endsWith(CLASS_SUFFIX) || name.endsWith(MODULE_INFO)) {
                        continue;
                    }

                    // Convert path to FQCN: replace '/' with '.' and strip .class
                    String fqcn = name.substring(0, name.length() - CLASS_SUFFIX.length()).replace('/', '.');
                    classNames.add(fqcn);
                }
            }
        }
        return classNames;
    }

    /**
     * Extracts all unique packages from a set of fully-qualified class names.
     */
    public static Set<String> extractPackages(Set<String> classNames) {
        Set<String> packages = new TreeSet<>();
        for (String className : classNames) {
            int lastDot = className.lastIndexOf('.');
            if (lastDot > 0) {
                packages.add(className.substring(0, lastDot));
            }
        }
        return packages;
    }

    /**
     * Derives minimal package roots from a set of class names.
     * <p>
     * Algorithm:
     * 1. Extract unique packages
     * 2. Subsumption: remove any package that has a shorter prefix already in the set
     * 3. Sibling merge (bottom-up, iterative): if 2+ packages share the same parent
     *    and the parent has depth > 1, replace them with the parent
     * 4. Final subsumption after merging
     */
    public static List<String> derivePackageRoots(Set<String> classNames) {
        Set<String> packages = extractPackages(classNames);
        if (packages.isEmpty()) {
            return List.of();
        }

        // Subsumption pass
        packages = subsume(packages);

        // Sibling merge (iterative until stable)
        boolean changed = true;
        while (changed) {
            changed = false;
            // Group by parent package
            Map<String, List<String>> byParent = packages.stream()
                    .filter(p -> p.contains("."))
                    .collect(Collectors.groupingBy(p -> p.substring(0, p.lastIndexOf('.'))));

            Set<String> merged = new TreeSet<>(packages);
            for (Map.Entry<String, List<String>> entry : byParent.entrySet()) {
                String parent = entry.getKey();
                List<String> siblings = entry.getValue();
                // Merge if 2+ siblings and parent has depth > 1 (contains a dot)
                if (siblings.size() >= 2 && parent.contains(".")) {
                    merged.removeAll(siblings);
                    merged.add(parent);
                    changed = true;
                }
            }
            packages = merged;
        }

        // Final subsumption
        packages = subsume(packages);

        return packages.stream().sorted().toList();
    }

    /// Returns the packages of the given classes, minus each one nested in another, so that
    /// `<package>.**` rules over the result cover every package and nothing beside them.
    public static List<String> coveringPackages(Set<String> classNames) {
        return subsume(extractPackages(classNames)).stream().sorted().toList();
    }

    /// Removes any package that is subsumed by a shorter prefix in the set.
    private static Set<String> subsume(Set<String> packages) {
        Set<String> result = new TreeSet<>();
        for (String pkg : packages) {
            boolean subsumed = false;
            for (String other : packages) {
                if (!other.equals(pkg) && pkg.startsWith(other + ".")) {
                    subsumed = true;
                    break;
                }
            }
            if (!subsumed) {
                result.add(pkg);
            }
        }
        return result;
    }
}
