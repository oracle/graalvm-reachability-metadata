/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.utils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

/**
 * Utilities for inspecting JAR files to derive package structures.
 */
public abstract class JarUtils {

    private static final String CLASS_SUFFIX = ".class";
    private static final String MODULE_INFO = "module-info.class";
    private static final String META_INF_VERSIONS = "META-INF/versions/";

    /**
     * Returns whether a resolved artifact is the unclassified main JAR eligible
     * for the coverage denominator. §TCK-test-harness.8
     */
    public static boolean isUnclassifiedMainJar(String extension, String classifier) {
        return "jar".equals(extension) && (classifier == null || classifier.isEmpty());
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
