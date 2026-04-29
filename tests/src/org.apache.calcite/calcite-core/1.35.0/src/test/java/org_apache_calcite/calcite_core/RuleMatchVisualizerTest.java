/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.calcite.plan.visualizer.RuleMatchVisualizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class RuleMatchVisualizerTest {
    @TempDir
    Path outputDirectory;

    @Test
    void writesVisualizerTemplateAndDataFiles() throws Exception {
        String outputSuffix = "-coverage";
        RuleMatchVisualizer visualizer = new RuleMatchVisualizer(
                outputDirectory.toString(), outputSuffix);

        visualizer.writeToFile();

        Path htmlOutput = outputDirectory.resolve("planner-viz" + outputSuffix + ".html");
        Path dataOutput = outputDirectory.resolve("planner-viz-data" + outputSuffix + ".js");
        assertThat(htmlOutput).exists();
        assertThat(dataOutput).exists();
        assertThat(Files.readString(htmlOutput))
                .contains("planner-viz-data" + outputSuffix + ".js")
                .doesNotContain("planner-viz-data.js");
        assertThat(Files.readString(dataOutput))
                .startsWith("var data = ")
                .contains("\"steps\"");
    }
}
