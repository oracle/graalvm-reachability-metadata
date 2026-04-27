/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_ivy.ivy;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.ivy.Main;
import org.apache.ivy.core.report.ResolveReport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class MainTest {
    @TempDir
    public Path tempDir;

    @Test
    public void launchesConfiguredMainClassWithResolvedArguments() throws Exception {
        Path ivyFile = tempDir.resolve("ivy.xml");
        Path cacheDir = tempDir.resolve("cache");
        Path markerFile = tempDir.resolve("launched-main.txt");
        Files.writeString(ivyFile, """
                <ivy-module version="2.0">
                    <info organisation="example" module="main-launcher-test" revision="working"/>
                    <configurations>
                        <conf name="default"/>
                    </configurations>
                </ivy-module>
                """, StandardCharsets.UTF_8);

        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        ResolveReport report;
        try {
            if (runningInNativeImage()) {
                report = Main.run(new String[] {
                        "-ivy", ivyFile.toString(),
                        "-cache", cacheDir.toString(),
                        "-confs", "default"
                });
            } else {
                report = Main.run(new String[] {
                        "-ivy", ivyFile.toString(),
                        "-cache", cacheDir.toString(),
                        "-confs", "default",
                        "-cp", launcherClasspath(),
                        "-main", LaunchTarget.class.getName(),
                        "-args", markerFile.toString(), "alpha", "beta"
                });
            }
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }

        assertThat(report.hasError()).isFalse();
        if (!runningInNativeImage()) {
            assertThat(Files.readAllLines(markerFile, StandardCharsets.UTF_8))
                    .containsExactly("alpha", "beta");
        }
    }

    private static String launcherClasspath() throws URISyntaxException {
        Set<String> entries = new LinkedHashSet<>();
        URL location = MainTest.class.getProtectionDomain().getCodeSource().getLocation();
        if (location != null) {
            entries.add(Path.of(location.toURI()).toString());
        }
        String classpath = System.getProperty("java.class.path");
        if (classpath != null && !classpath.isBlank()) {
            for (String entry : classpath.split(File.pathSeparator)) {
                if (!entry.isBlank()) {
                    entries.add(entry);
                }
            }
        }
        return String.join(File.pathSeparator, entries);
    }

    private static boolean runningInNativeImage() {
        return System.getProperty("java.vm.name", "").contains("Substrate");
    }
}
