/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io.quarkus.bootstrap.jbang;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public final class JBangBuilderImpl {
    private static Path appClasses;
    private static Path pomFile;
    private static List<Map.Entry<String, String>> repositories;
    private static List<Map.Entry<String, Path>> dependencies;
    private static Properties configurationProperties;
    private static boolean nativeImage;
    private static ClassLoader contextClassLoader;
    private static int invocationCount;

    private JBangBuilderImpl() {
    }

    public static Map<String, Object> postBuild(Path appClasses, Path pomFile,
            List<Map.Entry<String, String>> repositories, List<Map.Entry<String, Path>> dependencies,
            Properties configurationProperties, boolean nativeImage) {
        JBangBuilderImpl.appClasses = appClasses;
        JBangBuilderImpl.pomFile = pomFile;
        JBangBuilderImpl.repositories = new ArrayList<>(repositories);
        JBangBuilderImpl.dependencies = new ArrayList<>(dependencies);
        JBangBuilderImpl.configurationProperties = new Properties();
        JBangBuilderImpl.configurationProperties.putAll(configurationProperties);
        JBangBuilderImpl.nativeImage = nativeImage;
        JBangBuilderImpl.contextClassLoader = Thread.currentThread().getContextClassLoader();
        JBangBuilderImpl.invocationCount++;

        Map<String, Object> result = new HashMap<>();
        result.put("status", "ok");
        result.put("native-image", nativeImage);
        return result;
    }

    public static void reset() {
        appClasses = null;
        pomFile = null;
        repositories = null;
        dependencies = null;
        configurationProperties = null;
        nativeImage = false;
        contextClassLoader = null;
        invocationCount = 0;
    }

    public static Path appClasses() {
        return appClasses;
    }

    public static Path pomFile() {
        return pomFile;
    }

    public static List<Map.Entry<String, String>> repositories() {
        return repositories;
    }

    public static List<Map.Entry<String, Path>> dependencies() {
        return dependencies;
    }

    public static Properties configurationProperties() {
        return configurationProperties;
    }

    public static boolean nativeImage() {
        return nativeImage;
    }

    public static ClassLoader contextClassLoader() {
        return contextClassLoader;
    }

    public static int invocationCount() {
        return invocationCount;
    }
}
