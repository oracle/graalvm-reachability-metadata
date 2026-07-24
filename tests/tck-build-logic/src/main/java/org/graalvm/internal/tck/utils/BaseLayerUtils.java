/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.utils;

import org.gradle.api.Project;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Resolves and identifies Native Image base layers. §TCK-test-harness.3
 */
public final class BaseLayerUtils {
    private static final String BASE_LAYER_FILE_PROPERTY = "tck.baseLayerFile";
    private static final String BASE_LAYER_DIR_PROPERTY = "tck.baseLayerDir";
    private static final String BASE_LAYER_DIR_ENVIRONMENT_VARIABLE = "GVM_TCK_BASE_LAYER_DIR";
    private static final String DEDICATED_LAYER_ROOT_PROPERTY = "tck.dedicatedLayerRoot";
    private static final String DEDICATED_LAYER_ROOT_ENVIRONMENT_VARIABLE =
            "GVM_TCK_DEDICATED_LAYER_ROOT";
    private static final List<String> JUNIT_RUNTIME_ARTIFACT_GROUPS = List.of(
            "junit",
            "org.apiguardian",
            "org.hamcrest",
            "org.junit",
            "org.opentest4j"
    );
    private static final List<String> BASE_LAYER_MODULES = List.of(
            "java.base",
            "java.management",
            "java.naming",
            "java.sql",
            "jdk.unsupported",
            "java.desktop",
            "java.scripting",
            "jdk.httpserver",
            "java.net.http",
            "java.sql.rowset",
            "jdk.jfr",
            "java.smartcardio",
            "java.transaction.xa",
            "java.security.sasl",
            "java.xml",
            "jdk.dynalink",
            "jdk.jsobject",
            "jdk.localedata",
            "jdk.xml.dom"
    );

    private BaseLayerUtils() {
    }

    public static File resolveBaseLayerFile(Project project) {
        String explicitFile = Objects.toString(project.findProperty(BASE_LAYER_FILE_PROPERTY), "").trim();
        if (!explicitFile.isEmpty()) {
            return project.file(explicitFile);
        }

        String baseLayerDir = Objects.toString(project.findProperty(BASE_LAYER_DIR_PROPERTY), "").trim();
        if (baseLayerDir.isEmpty()) {
            baseLayerDir = Objects.toString(System.getenv(BASE_LAYER_DIR_ENVIRONMENT_VARIABLE), "").trim();
        }
        File directory = baseLayerDir.isEmpty()
                ? project.getLayout().getBuildDirectory().dir("native-base-layer").get().getAsFile()
                : project.file(baseLayerDir);
        return new File(directory, "base-layer.nil");
    }

    public static File resolveDedicatedLayerFile(Project project, String coordinates) {
        String baseLayerRoot = Objects.toString(project.findProperty(DEDICATED_LAYER_ROOT_PROPERTY), "").trim();
        if (baseLayerRoot.isEmpty()) {
            baseLayerRoot = Objects.toString(
                    System.getenv(DEDICATED_LAYER_ROOT_ENVIRONMENT_VARIABLE), "").trim();
        }
        File root = baseLayerRoot.isEmpty()
                ? project.getLayout().getBuildDirectory().dir("native-dedicated-layers").get().getAsFile()
                : project.file(baseLayerRoot);
        return new File(new File(root, sha256(coordinates)), "libDedicated.nil");
    }

    public static String layerCreateArgument(File baseLayerFile) {
        String modules = String.join(",", BASE_LAYER_MODULES.stream()
                .map(module -> "module=" + module)
                .toList());
        return "-H:LayerCreate=" + baseLayerFile.getName() + "," + modules;
    }

    public static List<String> layerModules() {
        return BASE_LAYER_MODULES;
    }

    public static boolean isJUnitRuntimeArtifact(String group, String artifact) {
        return group.startsWith("org.junit.") || JUNIT_RUNTIME_ARTIFACT_GROUPS.contains(group) ||
                (group.equals("org.graalvm.buildtools") && artifact.equals("junit-platform-native"));
    }

    public static Map<String, Object> expectedManifest(
            File baseLayerFile,
            String nativeImageVersion,
            String nativeImageMode,
            List<String> nativeImageArgs
    ) {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("nativeImageVersion", nativeImageVersion);
        manifest.put("osName", System.getProperty("os.name"));
        manifest.put("osArch", System.getProperty("os.arch"));
        manifest.put("nativeImageMode", nativeImageMode);
        manifest.put("nativeImageArgs", nativeImageArgs);
        manifest.put("layerCreateArg", layerCreateArgument(baseLayerFile));
        return manifest;
    }

    private static String sha256(String value) {
        MessageDigest digest = newSha256Digest();
        return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static MessageDigest newSha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

}
