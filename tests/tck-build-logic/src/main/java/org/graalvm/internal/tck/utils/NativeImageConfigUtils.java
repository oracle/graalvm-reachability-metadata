/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.utils;

import org.gradle.api.GradleException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Utility methods for reading and expanding native-image CI configuration.
 */
public final class NativeImageConfigUtils {

    public static final String DEFAULT_MODE = "current-defaults";

    private NativeImageConfigUtils() {
    }

    /**
     * Resolves the active native-image mode, preferring the environment override over the Gradle property.
     */
    public static String resolveSelectedMode(String environmentMode, String propertyMode) {
        if (environmentMode != null && !environmentMode.isBlank()) {
            return environmentMode;
        }
        if (propertyMode != null && !propertyMode.isBlank()) {
            return propertyMode;
        }
        return DEFAULT_MODE;
    }

    /**
     * Returns the ordered common native-image build arguments.
     */
    public static List<String> baseBuildArgs(Map<String, Object> ci) {
        return requireStringList(ci, "buildArgs", "ci.json");
    }

    /**
     * Returns the ordered native-image modes and their additional build arguments.
     */
    public static Map<String, List<String>> nativeImageModes(Map<String, Object> ci) {
        Object modesValue = ci.get("nativeImageModes");
        if (!(modesValue instanceof Map<?, ?> rawModes) || rawModes.isEmpty()) {
            throw new GradleException("ci.json must contain a non-empty nativeImageModes object");
        }

        Map<String, List<String>> modes = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawModes.entrySet()) {
            if (!(entry.getKey() instanceof String modeName) || modeName.isBlank()) {
                throw new GradleException("ci.json nativeImageModes must use non-empty string keys");
            }
            if (modes.containsKey(modeName)) {
                throw new GradleException("ci.json nativeImageModes must not contain duplicate mode '" + modeName + "'");
            }
            modes.put(modeName, requireStringList(rawModes, modeName, "ci.json nativeImageModes"));
        }
        return modes;
    }

    /**
     * Returns the resolved native-image arguments for the selected mode with placeholders expanded.
     */
    public static List<String> resolvedBuildArgs(
            Map<String, Object> ci,
            String selectedMode,
            Map<String, String> placeholders
    ) {
        Map<String, List<String>> modes = nativeImageModes(ci);
        List<String> modeArgs = modes.get(selectedMode);
        if (modeArgs == null) {
            throw new GradleException("Unknown native-image mode '" + selectedMode + "'. Available modes: " + modes.keySet());
        }

        List<String> args = new ArrayList<>(baseBuildArgs(ci).size() + modeArgs.size());
        args.addAll(baseBuildArgs(ci));
        args.addAll(modeArgs);
        return applyPlaceholders(args, placeholders);
    }

    /**
     * Returns the ordered mode names defined in the CI configuration.
     */
    public static List<String> modeNames(Map<String, Object> ci) {
        return new ArrayList<>(nativeImageModes(ci).keySet());
    }

    /**
     * Returns the Java versions allowed for each native-image mode, falling back to the matrix defaults.
     */
    public static Map<String, List<String>> javaVersionsByMode(
            Map<String, Object> ci,
            List<String> defaultJavaVersions,
            List<String> nativeImageModes
    ) {
        Object overridesValue = ci.get("nativeImageModeJavaVersions");
        Map<String, List<String>> overrides = new LinkedHashMap<>();
        if (overridesValue != null) {
            if (!(overridesValue instanceof Map<?, ?> rawOverrides)) {
                throw new GradleException("ci.json field 'nativeImageModeJavaVersions' must be an object when present");
            }
            for (Map.Entry<?, ?> entry : rawOverrides.entrySet()) {
                if (!(entry.getKey() instanceof String modeName) || modeName.isBlank()) {
                    throw new GradleException("ci.json nativeImageModeJavaVersions must use non-empty string keys");
                }
                if (!nativeImageModes.contains(modeName)) {
                    throw new GradleException("ci.json nativeImageModeJavaVersions contains unknown mode '" + modeName + "'");
                }
                List<String> versions = requireStringList(rawOverrides, modeName, "ci.json nativeImageModeJavaVersions");
                if (versions.isEmpty()) {
                    throw new GradleException("ci.json nativeImageModeJavaVersions for mode '" + modeName + "' must not be empty");
                }
                overrides.put(modeName, new ArrayList<>(new LinkedHashSet<>(versions)));
            }
        }

        List<String> normalizedDefaultJavaVersions = new ArrayList<>(new LinkedHashSet<>(defaultJavaVersions));
        if (normalizedDefaultJavaVersions.isEmpty()) {
            throw new GradleException("Matrix Java versions must not be empty");
        }

        Map<String, List<String>> javaVersionsByMode = new LinkedHashMap<>();
        for (String nativeImageMode : new LinkedHashSet<>(nativeImageModes)) {
            javaVersionsByMode.put(
                    nativeImageMode,
                    overrides.getOrDefault(nativeImageMode, normalizedDefaultJavaVersions)
            );
        }
        return javaVersionsByMode;
    }

    /**
     * Expands a matrix include list with Java, OS, and native-image mode dimensions.
     */
    public static List<Map<String, Object>> expandMatrixEntries(
            List<Map<String, Object>> entries,
            List<String> javaVersions,
            List<String> operatingSystems,
            List<String> nativeImageModes,
            Map<String, List<String>> javaVersionsByMode
    ) {
        List<Map<String, Object>> include = new ArrayList<>();
        List<String> normalizedJavaVersions = new ArrayList<>(new LinkedHashSet<>(javaVersions));
        List<String> normalizedOperatingSystems = new ArrayList<>(new LinkedHashSet<>(operatingSystems));
        List<String> normalizedNativeImageModes = new ArrayList<>(new LinkedHashSet<>(nativeImageModes));
        for (Map<String, Object> entry : entries) {
            for (String javaVersion : normalizedJavaVersions) {
                for (String operatingSystem : normalizedOperatingSystems) {
                    for (String nativeImageMode : normalizedNativeImageModes) {
                        List<String> versionsForMode = javaVersionsByMode.get(nativeImageMode);
                        if (versionsForMode == null || !versionsForMode.contains(javaVersion)) {
                            continue;
                        }
                        Map<String, Object> matrixEntry = new LinkedHashMap<>(entry);
                        matrixEntry.put("version", javaVersion);
                        matrixEntry.put("os", operatingSystem);
                        matrixEntry.put("nativeImageMode", nativeImageMode);
                        include.add(matrixEntry);
                    }
                }
            }
        }
        return include;
    }

    private static List<String> applyPlaceholders(List<String> args, Map<String, String> placeholders) {
        List<String> resolvedArgs = new ArrayList<>(args.size());
        for (String arg : args) {
            String resolvedArg = arg;
            for (Map.Entry<String, String> placeholder : placeholders.entrySet()) {
                resolvedArg = resolvedArg.replace(placeholder.getKey(), placeholder.getValue());
            }
            resolvedArgs.add(resolvedArg);
        }
        return resolvedArgs;
    }

    private static List<String> requireStringList(Map<?, ?> container, String fieldName, String owner) {
        Object value = container.get(fieldName);
        if (!(value instanceof List<?> values)) {
            throw new GradleException(owner + " must contain " + fieldName);
        }

        List<String> stringValues = new ArrayList<>();
        for (Object element : values) {
            if (element == null) {
                throw new GradleException(owner + " field '" + fieldName + "' must not contain null values");
            }
            stringValues.add(element.toString());
        }
        return stringValues;
    }
}
