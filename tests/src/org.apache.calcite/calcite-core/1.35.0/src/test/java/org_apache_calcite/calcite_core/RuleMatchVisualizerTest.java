/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import org.apache.calcite.plan.visualizer.RuleMatchVisualizer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class RuleMatchVisualizerTest {
    @Test
    void writesBundledTemplateAndDataScript(@TempDir Path tempDir) throws IOException {
        String suffix = "-reachability";
        RuleMatchVisualizer visualizer = new RuleMatchVisualizer(tempDir.toString(), suffix);

        visualizer.writeToFile();

        Path htmlOutput = tempDir.resolve("planner-viz" + suffix + ".html");
        Path dataOutput = tempDir.resolve("planner-viz-data" + suffix + ".js");

        assertThat(htmlOutput).exists().isRegularFile();
        assertThat(dataOutput).exists().isRegularFile();

        String htmlContent = Files.readString(htmlOutput);
        assertThat(htmlContent)
                .contains("src=\"planner-viz-data" + suffix + ".js\"")
                .doesNotContain("src=\"planner-viz-data.js\"");

        assertThat(Files.readString(dataOutput))
                .startsWith("var data = ")
                .contains("\"steps\"");
    }
}
