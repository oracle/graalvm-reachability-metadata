/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_ivy.ivy;

import org.apache.ivy.Main;
import org.apache.ivy.core.report.ResolveReport;
import org.apache.ivy.tools.analyser.RepositoryAnalyser;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opentest4j.TestAbortedException;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class MainTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void launchesConfiguredMainClassFromExtraClasspath() throws Exception {
        Path ivyFile = temporaryDirectory.resolve("ivy.xml");
        Path cacheDirectory = temporaryDirectory.resolve("cache");
        Files.writeString(ivyFile, """
                <ivy-module version="2.0">
                    <info organisation="example" module="main-launcher"/>
                </ivy-module>
                """, StandardCharsets.UTF_8);

        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            ResolveReport report = Main.run(new String[] {
                    "-ivy", ivyFile.toString(),
                    "-cache", cacheDirectory.toString(),
                    "-cp", ivyLibraryLocation().toString(),
                    "-main", RepositoryAnalyser.class.getName()
            });

            assertThat(report).isNotNull();
            assertThat(report.hasError()).isFalse();
        } catch (RuntimeException exception) {
            if (isUnsupportedNativeImageClasspathReload(exception)) {
                throw new TestAbortedException(
                        "Native image runtime does not support reloading Ivy classes via isolated URLClassLoader",
                        exception
                );
            }
            throw exception;
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }

    private static Path ivyLibraryLocation() throws Exception {
        URI location = RepositoryAnalyser.class.getProtectionDomain().getCodeSource()
                .getLocation().toURI();
        return Path.of(location);
    }

    private static boolean isUnsupportedNativeImageClasspathReload(final RuntimeException exception) {
        if (!"runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"))) {
            return false;
        }

        Throwable current = exception;
        while (current != null) {
            if (current instanceof ClassNotFoundException
                    && RepositoryAnalyser.class.getName().equals(current.getMessage())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
