/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_quarkus.quarkus_bootstrap_core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opentest4j.TestAbortedException;

public class JBangDevModeLauncherImplTest {

    private static final String LAUNCHER_CLASS = "io.quarkus.bootstrap.jbang.JBangDevModeLauncherImpl";

    @TempDir
    Path tempDir;

    @Test
    void readsJbangLaunchResourcesFromLauncherClassLoader() throws Exception {
        try {
            Path sourceFile = tempDir.resolve("JBangResourceSmokeTest.java");
            Files.writeString(sourceFile, """
                    //Q:CONFIG quarkus.log.level=INFO
                    public class JBangResourceSmokeTest {
                    }
                    """);

            Path launcherResources = tempDir.resolve("jbang-launcher-resources.jar");
            createLauncherResourcesJar(launcherResources, sourceFile);

            List<URL> classPath = new ArrayList<>();
            classPath.add(launcherResources.toUri().toURL());
            for (String entry : System.getProperty("java.class.path").split(Pattern.quote(File.pathSeparator))) {
                if (!entry.isBlank()) {
                    classPath.add(Path.of(entry).toUri().toURL());
                }
            }

            try (URLClassLoader launcherClassLoader = new URLClassLoader(classPath.toArray(URL[]::new),
                    ClassLoader.getPlatformClassLoader())) {
                Class<?> launcherClass;
                try {
                    launcherClass = Class.forName(LAUNCHER_CLASS, true, launcherClassLoader);
                } catch (ClassNotFoundException exception) {
                    if (isNativeImageRuntime()) {
                        throw new TestAbortedException(
                                "Native image runtime does not support reloading JBang launcher classes via isolated URLClassLoader",
                                exception);
                    }
                    throw exception;
                }
                if (launcherClass.getClassLoader() != launcherClassLoader) {
                    return;
                }
                Method main = launcherClass.getMethod("main", String[].class);

                try {
                    main.invoke(null, (Object) new String[] {"--debug"});
                    fail("Expected the deliberately invalid forced dependency to fail launcher startup");
                } catch (InvocationTargetException exception) {
                    assertThat(exception.getCause())
                            .isInstanceOf(RuntimeException.class)
                            .hasRootCauseMessage("Invalid artifact invalid-coordinate=unused");
                }
            }
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static boolean isNativeImageRuntime() {
        return "runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"));
    }

    private static void createLauncherResourcesJar(Path jarPath, Path sourceFile) throws IOException {
        try (OutputStream file = Files.newOutputStream(jarPath);
                ZipOutputStream zip = new ZipOutputStream(file)) {
            zip.putNextEntry(new ZipEntry("jbang-dev.dat"));
            DataOutputStream data = new DataOutputStream(zip);
            data.writeUTF(minimalPom());
            data.writeUTF(sourceFile.toString());
            data.writeInt(1);
            data.writeUTF("invalid-coordinate");
            data.writeUTF("unused");
            data.flush();
            zip.closeEntry();

            zip.putNextEntry(new ZipEntry("quarkus-version.txt"));
            zip.write("999-SNAPSHOT".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
    }

    private static String minimalPom() {
        return """
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>io.quarkus.test</groupId>
                    <artifactId>jbang-resource-smoke-test</artifactId>
                    <version>1.0.0</version>
                </project>
                """;
    }
}
