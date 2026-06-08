/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop.hadoop_common;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.apache.hadoop.util.RunJar;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class RunJarTest {
    private static final String RESULT_PROPERTY = RunJarTest.class.getName() + ".result";

    @TempDir
    Path tempDir;

    @Test
    void mainLoadsAndInvokesConfiguredMainClass() throws Throwable {
        Path jar = tempDir.resolve("job.jar");
        createJobJar(jar);
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        String previousResult = System.getProperty(RESULT_PROPERTY);
        System.clearProperty(RESULT_PROPERTY);

        String result = null;
        try {
            RunJar.main(new String[] {
                    jar.toString(),
                    RunJarInvocationTarget.class.getName(),
                    "alpha",
                    "beta"
            });
            result = System.getProperty(RESULT_PROPERTY);
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
            return;
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
            restoreProperty(previousResult);
        }

        assertThat(result).isEqualTo("alpha,beta");
    }

    private static void createJobJar(Path jar) throws IOException {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar), manifest)) {
            JarEntry entry = new JarEntry("job-resource.txt");
            output.putNextEntry(entry);
            output.write("job".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
    }

    private static void restoreProperty(String previousResult) {
        if (previousResult == null) {
            System.clearProperty(RESULT_PROPERTY);
        } else {
            System.setProperty(RESULT_PROPERTY, previousResult);
        }
    }

    public static class RunJarInvocationTarget {
        public static void main(String[] args) {
            System.setProperty(RESULT_PROPERTY, String.join(",", args));
        }
    }
}
