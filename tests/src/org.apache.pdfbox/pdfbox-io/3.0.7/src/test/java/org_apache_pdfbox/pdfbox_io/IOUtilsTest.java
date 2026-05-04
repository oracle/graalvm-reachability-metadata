/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pdfbox.pdfbox_io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.LogFactory;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.io.RandomAccessReadMemoryMappedFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class IOUtilsTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void copiesTheCompleteInputStream() throws IOException {
        byte[] expected = "PDFBox IOUtils copy payload".getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        long copiedBytes = IOUtils.copy(new ByteArrayInputStream(expected), output);

        assertThat(copiedBytes).isEqualTo(expected.length);
        assertThat(output.toByteArray()).isEqualTo(expected);
        assertThat(IOUtils.toByteArray(new ByteArrayInputStream(expected))).isEqualTo(expected);
    }

    @Test
    void unmapsDirectBuffersWithoutExposingPlatformCleanerDetails() {
        ByteBuffer directBuffer = ByteBuffer.allocateDirect(32);
        directBuffer.putInt(0, 0x50444632);

        assertThatCode(() -> IOUtils.unmap(directBuffer)).doesNotThrowAnyException();
        assertThatCode(() -> IOUtils.unmap(null)).doesNotThrowAnyException();
    }

    @Test
    void closesMemoryMappedReaderAndUnmapsItsBuffer() throws IOException {
        byte[] content = "memory mapped pdfbox io".getBytes(StandardCharsets.UTF_8);
        Path mappedFile = temporaryDirectory.resolve("mapped-input.bin");
        Files.write(mappedFile, content);

        try (RandomAccessReadMemoryMappedFile reader = new RandomAccessReadMemoryMappedFile(mappedFile)) {
            byte[] actual = new byte[content.length];

            assertThat(reader.length()).isEqualTo(content.length);
            assertThat(reader.read(actual, 0, actual.length)).isEqualTo(content.length);
            assertThat(actual).isEqualTo(content);
        }

        assertThatCode(() -> Files.delete(mappedFile)).doesNotThrowAnyException();
    }

    @Test
    void initializesFallbackUnmapperWhenUnsafeCleanerIsUnavailable() throws Exception {
        Optional<String> javaAgentArgument = jacocoJavaAgentArgument();
        if (javaAgentArgument.isEmpty()) {
            assertThatCode(() -> IOUtils.unmap(ByteBuffer.allocateDirect(1))).doesNotThrowAnyException();
            return;
        }

        ProcessResult result = runFallbackProbe(javaAgentArgument.get());

        assertThat(result.exitCode()).as(result.output()).isEqualTo(0);
        assertThat(result.output()).contains("fallback-unmapper-initialized");
    }

    private static Optional<String> jacocoJavaAgentArgument() {
        return ManagementFactory.getRuntimeMXBean()
                .getInputArguments()
                .stream()
                .filter(argument -> argument.startsWith("-javaagent:") && argument.contains("jacoco"))
                .findFirst()
                .map(IOUtilsTest::forceJacocoAppend);
    }

    private static String forceJacocoAppend(String javaAgentArgument) {
        if (javaAgentArgument.contains("append=")) {
            return javaAgentArgument.replace("append=false", "append=true");
        }
        if (javaAgentArgument.contains("=")) {
            return javaAgentArgument + ",append=true";
        }
        return javaAgentArgument + "=append=true";
    }

    private static ProcessResult runFallbackProbe(String javaAgentArgument) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.add(javaAgentArgument);
        command.add("--limit-modules=java.base,java.instrument,java.logging,java.management,java.naming,java.xml");
        command.add("--add-opens=java.base/java.nio=ALL-UNNAMED");
        command.add("--add-exports=java.base/jdk.internal.ref=ALL-UNNAMED");
        command.add("-cp");
        command.add(probeClasspath());
        command.add(IOUtilsFallbackUnmapperProbe.class.getName());

        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
        boolean completed = process.waitFor(30, TimeUnit.SECONDS);
        if (!completed) {
            process.destroyForcibly();
            process.waitFor(5, TimeUnit.SECONDS);
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(completed).as(output).isTrue();
        return new ProcessResult(process.exitValue(), output);
    }

    private static String probeClasspath() throws Exception {
        return String.join(
                File.pathSeparator,
                codeSourceLocation(IOUtilsFallbackUnmapperProbe.class),
                codeSourceLocation(IOUtils.class),
                codeSourceLocation(LogFactory.class));
    }

    private static String codeSourceLocation(Class<?> type) throws Exception {
        return Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
    }

    private record ProcessResult(int exitCode, String output) {
    }
}

final class IOUtilsFallbackUnmapperProbe {

    private IOUtilsFallbackUnmapperProbe() {
    }

    public static void main(String[] args) {
        IOUtils.unmap(ByteBuffer.allocateDirect(1));
        System.out.println("fallback-unmapper-initialized");
    }
}
