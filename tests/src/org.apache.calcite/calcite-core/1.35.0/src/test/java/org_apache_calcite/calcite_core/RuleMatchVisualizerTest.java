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

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class RuleMatchVisualizerTest {
    @Test
    void writesVisualizationUsingBundledHtmlTemplate(@TempDir Path outputDirectory)
            throws Exception {
        RuleMatchVisualizer visualizer =
                new RuleMatchVisualizer(outputDirectory.toString(), "-query");

        visualizer.writeToFile();

        Path html = outputDirectory.resolve("planner-viz-query.html");
        Path data = outputDirectory.resolve("planner-viz-data-query.js");
        assertThat(Files.readString(html)).contains("src=\"planner-viz-data-query.js\"");
        assertThat(Files.readString(data)).startsWith("var data = {").contains("\"steps\"");
    }
}
