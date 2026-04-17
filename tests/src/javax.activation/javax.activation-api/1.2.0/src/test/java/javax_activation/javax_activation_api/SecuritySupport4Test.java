/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_activation.javax_activation_api;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.util.Locale;

import javax.activation.MimetypesFileTypeMap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class SecuritySupport4Test {
    @Test
    void bootstrapLoadedMimeTypesFileTypeMapUsesSystemResources() throws Exception {
        Path resourceRoot = Files.createTempDirectory("security-support-4");
        Path metaInfDirectory = Files.createDirectories(resourceRoot.resolve("META-INF"));
        Files.writeString(
            metaInfDirectory.resolve("mime.types"),
            "application/x-security-support-4 securitysupport4\n",
            StandardCharsets.UTF_8
        );

        Path testClassesPath = codeSourcePath(BootstrapActivationProbe.class);
        Path activationJarPath = codeSourcePath(MimetypesFileTypeMap.class);
        Path javaExecutablePath = resolveJavaExecutable();

        Process process = new ProcessBuilder(
            javaExecutablePath.toString(),
            "-Xbootclasspath/a:" + activationJarPath,
            "-cp",
            testClassesPath + java.io.File.pathSeparator + resourceRoot,
            BootstrapActivationProbe.class.getName()
        ).redirectErrorStream(true).start();

        String output;
        try (java.io.InputStream inputStream = process.getInputStream()) {
            output = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }

        int exitCode = process.waitFor();
        assertEquals(0, exitCode, output);
    }

    private static Path codeSourcePath(Class<?> type) throws Exception {
        CodeSource codeSource = type.getProtectionDomain().getCodeSource();
        assertNotNull(codeSource, () -> "No code source found for " + type.getName());
        return Paths.get(codeSource.getLocation().toURI());
    }

    private static Path resolveJavaExecutable() {
        String javaExecutableName = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")
            ? "java.exe"
            : "java";
        return Paths.get(System.getProperty("java.home"), "bin", javaExecutableName);
    }
}

final class BootstrapActivationProbe {
    private BootstrapActivationProbe() {
    }

    public static void main(String[] args) {
        Thread.currentThread().setContextClassLoader(null);

        MimetypesFileTypeMap fileTypeMap = new MimetypesFileTypeMap();
        String contentType = fileTypeMap.getContentType("sample.securitysupport4");
        if (!"application/x-security-support-4".equals(contentType)) {
            System.err.println("Expected application/x-security-support-4 but was " + contentType);
            System.exit(1);
        }
    }
}
