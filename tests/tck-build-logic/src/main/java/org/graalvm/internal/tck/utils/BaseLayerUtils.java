/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.utils;

import org.gradle.api.Project;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Resolves the shared Native Image base-layer location. §TCK-test-harness.3
 */
public final class BaseLayerUtils {
    private static final String BASE_LAYER_FILE_PROPERTY = "tck.baseLayerFile";
    private static final String BASE_LAYER_DIR_PROPERTY = "tck.baseLayerDir";
    private static final String BASE_LAYER_DIR_ENVIRONMENT_VARIABLE = "GVM_TCK_BASE_LAYER_DIR";
    private static final String BASE_LAYER_MODULES = String.join(",", List.of(
            "module=java.base",
            "module=java.management",
            "module=java.naming",
            "module=java.sql",
            "module=jdk.unsupported",
            "module=java.desktop",
            "module=java.scripting",
            "module=jdk.httpserver",
            "module=java.net.http",
            "module=java.sql.rowset",
            "module=jdk.jfr",
            "module=java.smartcardio",
            "module=java.transaction.xa",
            "module=java.security.sasl",
            "module=java.xml",
            "module=jdk.dynalink",
            "module=jdk.jsobject",
            "module=jdk.localedata",
            "module=jdk.xml.dom"
    ));

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
        manifest.put("layerCreateArg", "-H:LayerCreate=" + baseLayerFile.getName() + "," + BASE_LAYER_MODULES);
        return manifest;
    }
}
