/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testng.testng;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testng.Converter;

public class ConverterTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void convertsXmlSuiteFileIntoYamlFile() throws Exception {
        Path inputFile = temporaryDirectory.resolve("suite.xml");
        Path outputDirectory = Files.createDirectory(temporaryDirectory.resolve("converted"));
        Files.writeString(inputFile, """
                <!DOCTYPE suite SYSTEM "http://testng.org/testng-1.0.dtd">
                <suite name="converted-suite">
                  <test name="converted-test">
                    <classes>
                      <class name="org_testng.testng.ConverterTest"/>
                    </classes>
                  </test>
                </suite>
                """, StandardCharsets.UTF_8);

        Converter.main(new String[] {"-d", outputDirectory.toString(), inputFile.toString()});

        Path outputFile = outputDirectory.resolve("suite.yaml");
        assertThat(outputFile).exists();
        assertThat(Files.readString(outputFile, StandardCharsets.UTF_8))
                .contains("converted-suite")
                .contains(ConverterTest.class.getName());
    }
}
