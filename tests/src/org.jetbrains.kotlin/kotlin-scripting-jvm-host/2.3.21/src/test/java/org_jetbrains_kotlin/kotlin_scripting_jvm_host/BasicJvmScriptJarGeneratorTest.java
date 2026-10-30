/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlin.kotlin_scripting_jvm_host;

import java.nio.file.Path;
import java.util.jar.JarFile;

import kotlin.script.experimental.api.EvaluationResult;
import kotlin.script.experimental.api.ResultValue;
import kotlin.script.experimental.api.ResultWithDiagnostics;
import kotlin.script.experimental.api.ScriptEvaluationConfiguration;
import kotlin.script.experimental.jvm.impl.KJvmCompiledScript;
import kotlin.script.experimental.jvmhost.BasicJvmScriptJarGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(120)
public class BasicJvmScriptJarGeneratorTest {
    @TempDir
    Path directory;

    @Test
    void packagesCompiledScriptAndItsMetadataWithoutExecutingIt() throws Exception {
        Path output = directory.resolve("greeting.jar");
        KJvmCompiledScript script = ScriptFixtures.compiledScript(ScriptFixtures.compilationConfiguration());
        ScriptEvaluationConfiguration configuration = ScriptFixtures.evaluationConfiguration();

        Object result = ScriptFixtures.evaluate(new BasicJvmScriptJarGenerator(output.toFile()), script, configuration);

        assertThat(result).isInstanceOf(ResultWithDiagnostics.Success.class);
        EvaluationResult evaluation = (EvaluationResult) ((ResultWithDiagnostics.Success<?>) result).getValue();
        assertThat(evaluation.getReturnValue()).isSameAs(ResultValue.NotEvaluated.INSTANCE);
        assertThat(evaluation.getConfiguration()).isSameAs(configuration);
        try (JarFile jar = new JarFile(output.toFile())) {
            assertThat(jar.getManifest().getMainAttributes().getValue("Main-Class")).isEqualTo(ScriptFixtures.CLASS_NAME);
            assertThat(jar.getJarEntry(ScriptFixtures.CLASS_ENTRY).getSize()).isEqualTo(ScriptFixtures.classBytes().length);
            assertThat(jar.getJarEntry("META-INF/kotlin/script/" + ScriptFixtures.CLASS_NAME + ".kotlin_script")).isNotNull();
            try (var input = jar.getInputStream(jar.getJarEntry(ScriptFixtures.RESOURCE_ENTRY))) {
                assertThat(input.readAllBytes()).isEqualTo(ScriptFixtures.RESOURCE_BYTES);
            }
        }
    }
}
