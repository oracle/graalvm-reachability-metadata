/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlin.kotlin_scripting_jvm_host;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import kotlin.Unit;
import kotlin.script.experimental.api.ScriptCompilationConfiguration;
import kotlin.script.experimental.api.ScriptCompilationKt;
import kotlin.script.experimental.jvm.JvmDependency;
import kotlin.script.experimental.jvmhost.JvmScriptSavingKt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(120)
public class JvmScriptSavingKtTest {
    @TempDir
    Path directory;

    @Test
    void savesOnlyExistingDistinctDependenciesInTheManifest() throws Exception {
        Path dependency = Files.createFile(directory.resolve("dependency.jar"));
        Path missing = directory.resolve("missing.jar");
        ScriptCompilationConfiguration configuration = new ScriptCompilationConfiguration(builder -> {
            builder.put(ScriptCompilationKt.getDependencies(ScriptCompilationConfiguration.Companion),
                    List.of(new JvmDependency(dependency.toFile(), dependency.toFile(), missing.toFile())));
            return Unit.INSTANCE;
        });
        Path archive = directory.resolve("script.jar");

        JvmScriptSavingKt.saveToJar(ScriptFixtures.compiledScript(configuration), archive.toFile());

        try (JarFile jar = new JarFile(archive.toFile())) {
            String classPath = jar.getManifest().getMainAttributes().getValue("Class-Path");
            assertThat(classPath.split(" ")).containsOnlyOnce(dependency.toFile().toURI().toURL().toExternalForm());
            assertThat(classPath).doesNotContain(missing.toFile().toURI().toURL().toExternalForm());
            assertThat(jar.getManifest().getMainAttributes().getValue("Created-By")).isEqualTo("JetBrains Kotlin");
        }
    }

    @Test
    void rejectsMissingDependenciesUnlessDependencyCheckingIsDisabled() throws Exception {
        Path archive = directory.resolve("script.jar");
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().putValue("Manifest-Version", "1.0");
        manifest.getMainAttributes().putValue("Main-Class", ScriptFixtures.CLASS_NAME);
        manifest.getMainAttributes().putValue("Class-Path", directory.resolve("absent.jar").toUri().toString());
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(archive), manifest)) {
            output.finish();
        }

        assertThat(JvmScriptSavingKt.loadScriptFromJar(archive.toFile(), true)).isNull();
        assertThat(JvmScriptSavingKt.loadScriptFromJar(archive.toFile(), false)).isNotNull();
    }
}
