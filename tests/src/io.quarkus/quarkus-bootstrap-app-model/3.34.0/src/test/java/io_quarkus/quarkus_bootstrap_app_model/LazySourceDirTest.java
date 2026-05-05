/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_quarkus.quarkus_bootstrap_app_model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.quarkus.bootstrap.workspace.LazySourceDir;
import io.quarkus.paths.PathFilter;

public class LazySourceDirTest {
    @TempDir
    Path tempDir;

    @Test
    public void serializesAndDeserializesConfiguredDirectoriesAndData() throws Exception {
        final Path sourcePath = tempDir.resolve("src/main/java");
        final Path outputPath = tempDir.resolve("target/classes");
        final Path generatedSourcesPath = tempDir.resolve("target/generated-sources/annotations");
        final PathFilter sourceFilter = PathFilter.forIncludes(List.of("**/*.java"));
        final PathFilter outputFilter = PathFilter.forExcludes(List.of("**/generated/**"));
        final Map<Object, Object> data = new HashMap<>();
        data.put("language", "java");

        final LazySourceDir sourceDir = new LazySourceDir(
                sourcePath,
                sourceFilter,
                outputPath,
                outputFilter,
                generatedSourcesPath,
                data);

        final LazySourceDir copy = serializeAndDeserialize(sourceDir);

        assertEquals(sourcePath.toAbsolutePath(), copy.getDir());
        assertEquals(outputPath.toAbsolutePath(), copy.getOutputDir());
        assertEquals(generatedSourcesPath.toAbsolutePath(), copy.getAptSourcesDir());
        assertEquals("java", copy.getValue("language", String.class));
        assertTrue(copy.toString().contains("src-filter="));
        assertTrue(copy.toString().contains("dest-filter="));
    }

    private LazySourceDir serializeAndDeserialize(final LazySourceDir sourceDir)
            throws IOException, ClassNotFoundException {
        final byte[] serialized;
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(sourceDir);
            output.flush();
            serialized = bytes.toByteArray();
        }

        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            return assertInstanceOf(LazySourceDir.class, input.readObject());
        }
    }
}
