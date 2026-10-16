/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.utils;

import org.gradle.api.GradleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RealUseUtilsTests {

    @TempDir
    Path tempDir;

    @Test
    void filterCoversEveryPackageOnceInTheRepositoryJsonFormat() throws IOException {
        File target = jar("target.jar", "com/example/lib/Client.class", "com/example/lib/internal/Codec.class");
        File supporting = jar("supporting.jar", "org/pool/Pool.class", "module-info.class");
        File filter = tempDir.resolve("user-code-filter.json").toFile();

        int rules = RealUseUtils.writeUserCodeFilter(filter, List.of(target, supporting, target));

        assertThat(rules).isEqualTo(2);
        assertThat(Files.readString(filter.toPath())).isEqualTo("""
                {
                  "rules": [
                    {
                      "excludeClasses": "**"
                    },
                    {
                      "includeClasses": "com.example.lib.**"
                    },
                    {
                      "includeClasses": "org.pool.**"
                    }
                  ]
                }
                """);
    }

    @Test
    void filterRejectsLibrariesWithoutPackages() throws IOException {
        File empty = jar("empty.jar", "module-info.class");

        assertThatThrownBy(() -> RealUseUtils.writeUserCodeFilter(tempDir.resolve("f.json").toFile(), List.of(empty)))
                .isInstanceOf(GradleException.class);
    }

    @Test
    void agentMetadataIsCopiedOrItsAbsenceReported() throws IOException {
        Path build = tempDir.resolve("build");
        Path output = tempDir.resolve("out");
        assertThatThrownBy(() -> RealUseUtils.copyAgentMetadata(build.toFile(), output.toString()))
                .isInstanceOf(GradleException.class);

        Path agent = Files.createDirectories(build.resolve("native/agent-output/test"));
        Files.writeString(agent.resolve("reachability-metadata.json"), "{}\n");
        RealUseUtils.copyAgentMetadata(build.toFile(), output.toString());

        assertThat(output.resolve("reachability-metadata.json")).hasContent("{}");
    }

    private File jar(String name, String... entries) throws IOException {
        Path path = tempDir.resolve(name);
        try (OutputStream file = Files.newOutputStream(path); JarOutputStream jar = new JarOutputStream(file)) {
            for (String entry : entries) {
                jar.putNextEntry(new JarEntry(entry));
                jar.closeEntry();
            }
        }
        return path.toFile();
    }
}
