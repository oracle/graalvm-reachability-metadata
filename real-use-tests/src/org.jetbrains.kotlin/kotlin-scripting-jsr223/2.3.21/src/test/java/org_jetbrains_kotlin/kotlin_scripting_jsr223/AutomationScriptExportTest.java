/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlin.kotlin_scripting_jsr223;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.script.ScriptEngineFactory;
import kotlin.script.experimental.jsr223.KotlinJsr223DefaultScriptEngineFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * JSR-223 source generation exports an automation workflow as a Kotlin script.
 *
 * A workflow designer turns configured service calls into a downloadable script for an operator
 * to review and run separately. Exporting the script does not compile or execute it.
 */
@Timeout(60)
public class AutomationScriptExportTest {

    @Test
    void exportsOrderedFulfillmentStepsForOperatorReview(@TempDir Path directory) throws IOException {
        ScriptEngineFactory factory = new KotlinJsr223DefaultScriptEngineFactory();
        WorkflowExporter exporter = new WorkflowExporter(factory);
        List<ServiceCall> workflow = List.of(
                new ServiceCall("fulfillment", "beginBatch", List.of("\"batch-104\"")),
                new ServiceCall("fulfillment", "reserve", List.of("\"SKU-42\"", "3")),
                new ServiceCall("fulfillment", "reserve", List.of("\"SKU-99\"", "1")),
                new ServiceCall("fulfillment", "commitBatch", List.of()));

        Path script = directory.resolve("fulfillment.kts");
        Files.writeString(script, exporter.render(workflow, "Batch ready for dispatch"),
                StandardCharsets.UTF_8);

        String newline = System.lineSeparator();
        String expected = String.join(newline,
                "fulfillment.beginBatch(\"batch-104\")",
                "fulfillment.reserve(\"SKU-42\", 3)",
                "fulfillment.reserve(\"SKU-99\", 1)",
                "fulfillment.commitBatch()",
                "print(\"Batch ready for dispatch\")") + newline;
        assertThat(Files.readString(script, StandardCharsets.UTF_8)).isEqualTo(expected);
    }

    private record ServiceCall(String receiver, String method, List<String> arguments) {
    }

    private record WorkflowExporter(ScriptEngineFactory factory) {
        String render(List<ServiceCall> calls, String completionMessage) {
            List<String> statements = new ArrayList<>();
            for (ServiceCall call : calls) {
                statements.add(factory.getMethodCallSyntax(call.receiver(), call.method(),
                        call.arguments().toArray(String[]::new)));
            }
            statements.add(factory.getOutputStatement(completionMessage));
            return factory.getProgram(statements.toArray(String[]::new));
        }
    }
}
