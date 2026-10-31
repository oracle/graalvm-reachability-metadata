/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlin.kotlin_scripting_jvm_host;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import kotlin.script.experimental.api.ScriptCompilationConfiguration;
import kotlin.script.experimental.host.StringScriptSource;
import kotlin.script.experimental.jvm.impl.KJvmCompiledScript;
import kotlin.script.experimental.jvmhost.CompiledScriptJarsCache;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Timeout(120)
public class CompiledScriptJarsCacheTest {
    @TempDir
    Path directory;

    @Test
    void storesCompiledScriptAndFindsItsArchiveOnTheNextLookup() throws Exception {
        Path archive = directory.resolve("greeting.jar");
        StringScriptSource source = new StringScriptSource("\"hello\"", "greeting.kts", "greeting.kts");
        ScriptCompilationConfiguration configuration = ScriptFixtures.compilationConfiguration();
        CompiledScriptJarsCache cache = new CompiledScriptJarsCache((script, config) -> archive.toFile());
        assertThat(cache.get(source, configuration)).isNull();
        assertThat(Files.exists(archive)).isFalse();

        KJvmCompiledScript compiled = ScriptFixtures.compiledScript(configuration);
        cache.store(compiled, source, configuration);

        assertThat(cache.get(source, configuration)).isNotNull();
        try (JarFile jar = new JarFile(archive.toFile())) {
            assertThat(jar.getManifest().getMainAttributes().getValue("Main-Class")).isEqualTo(ScriptFixtures.CLASS_NAME);
            try (var input = jar.getInputStream(jar.getJarEntry(ScriptFixtures.CLASS_ENTRY))) {
                assertThat(input.readAllBytes()).isEqualTo(ScriptFixtures.classBytes());
            }
        }
    }

    @Test
    void invalidatesAnArchiveThatDoesNotDescribeAScript() throws Exception {
        Path archive = directory.resolve("not-a-script.jar");
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().putValue("Manifest-Version", "1.0");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(archive), manifest)) {
            output.finish();
        }
        CompiledScriptJarsCache cache = new CompiledScriptJarsCache((script, config) -> archive.toFile());

        assertThat(cache.get(new StringScriptSource("1", "sample.kts", null), ScriptFixtures.compilationConfiguration())).isNull();
        assertThat(Files.exists(archive)).isFalse();
    }

    @Test
    void rejectsAScriptWithNoCacheFileMapping() {
        CompiledScriptJarsCache cache = new CompiledScriptJarsCache((script, config) -> null);

        assertThatThrownBy(() -> cache.get(new StringScriptSource("1", "sample.kts", null), ScriptFixtures.compilationConfiguration()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unable to find a mapping to a file");
    }
}
