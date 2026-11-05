/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_ant.ant_junit;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.optional.junit.AggregateTransformer;
import org.apache.tools.ant.taskdefs.optional.junit.XMLResultAggregator;
import org.apache.tools.ant.types.FileSet;
import org.apache.xalan.processor.TransformerFactoryImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class AggregateTransformerTest {
    @TempDir
    Path directory;

    @Test
    void generatesAnHtmlReportUsingTheBundledStylesheet() throws Exception {
        Files.writeString(directory.resolve("TEST-consumer.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <testsuite name="example.ConsumerSuite" tests="1" failures="0" errors="0"
                           skipped="0" time="0.1" timestamp="2025-01-01T00:00:00" hostname="localhost">
                  <properties/>
                  <testcase classname="example.ConsumerSuite" name="consumer-check" time="0.1"/>
                  <system-out>consumer-output</system-out>
                  <system-err/>
                </testsuite>
                """);
        Project project = new Project();
        project.init();
        project.setBaseDir(directory.toFile());
        XMLResultAggregator aggregator = new XMLResultAggregator();
        aggregator.setProject(project);
        aggregator.setTodir(directory.toFile());
        FileSet input = new FileSet();
        input.setDir(directory.toFile());
        input.setIncludes("TEST-consumer.xml");
        aggregator.addFileSet(input);

        AggregateTransformer transformer = aggregator.createReport();
        AggregateTransformer.Format format = new AggregateTransformer.Format();
        format.setValue(AggregateTransformer.NOFRAMES);
        transformer.setFormat(format);
        transformer.setTodir(directory.toFile());
        // Interpret the stylesheet instead of defining XSLTC translet classes at runtime.
        transformer.createFactory().setName(TransformerFactoryImpl.class.getName());

        aggregator.execute();

        String html = Files.readString(directory.resolve("junit-noframes.html"));
        assertThat(html).contains("ConsumerSuite", "consumer-check", "Summary");
        assertThat(Files.readString(aggregator.getDestinationFile().toPath()))
                .contains("package=\"example\"", "name=\"ConsumerSuite\"");
    }
}
