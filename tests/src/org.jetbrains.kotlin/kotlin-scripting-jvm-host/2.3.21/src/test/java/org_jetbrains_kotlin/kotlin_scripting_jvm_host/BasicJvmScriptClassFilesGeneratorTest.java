/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlin.kotlin_scripting_jvm_host;

import java.nio.file.Files;
import java.nio.file.Path;

import kotlin.script.experimental.api.EvaluationResult;
import kotlin.script.experimental.api.ResultValue;
import kotlin.script.experimental.api.ResultWithDiagnostics;
import kotlin.script.experimental.api.ScriptEvaluationConfiguration;
import kotlin.script.experimental.jvm.impl.KJvmCompiledScript;
import kotlin.script.experimental.jvmhost.BasicJvmScriptClassFilesGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(120)
public class BasicJvmScriptClassFilesGeneratorTest {
    @TempDir
    Path directory;

    @Test
    void writesCompiledClassesAndResourcesWithoutExecutingTheScript() throws Exception {
        KJvmCompiledScript script = ScriptFixtures.compiledScript(ScriptFixtures.compilationConfiguration());
        ScriptEvaluationConfiguration configuration = ScriptFixtures.evaluationConfiguration();
        Path output = directory.resolve("classes");

        Object result = ScriptFixtures.evaluate(new BasicJvmScriptClassFilesGenerator(output.toFile()), script, configuration);

        assertThat(result).isInstanceOf(ResultWithDiagnostics.Success.class);
        EvaluationResult evaluation = (EvaluationResult) ((ResultWithDiagnostics.Success<?>) result).getValue();
        assertThat(evaluation.getReturnValue()).isSameAs(ResultValue.NotEvaluated.INSTANCE);
        assertThat(evaluation.getConfiguration()).isSameAs(configuration);
        assertThat(Files.readAllBytes(output.resolve(ScriptFixtures.CLASS_ENTRY))).isEqualTo(ScriptFixtures.classBytes());
        assertThat(Files.readAllBytes(output.resolve(ScriptFixtures.RESOURCE_ENTRY))).isEqualTo(ScriptFixtures.RESOURCE_BYTES);
    }
}
