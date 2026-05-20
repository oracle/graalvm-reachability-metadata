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
    void convertsXmlSuiteIntoYamlFile() throws Exception {
        Path inputFile = temporaryDirectory.resolve("converter-suite.xml");
        Path outputDirectory = Files.createDirectory(temporaryDirectory.resolve("output"));
        Files.writeString(inputFile, """
                <!DOCTYPE suite SYSTEM "https://testng.org/testng-1.0.dtd">
                <suite name="converter-suite">
                  <test name="converter-test">
                    <classes>
                      <class name="%s"/>
                    </classes>
                  </test>
                </suite>
                """.formatted(ConverterTest.class.getName()), StandardCharsets.UTF_8);

        Converter.main(new String[] {"-d", outputDirectory.toString(), inputFile.toString()});

        Path outputFile = outputDirectory.resolve("converter-suite.yaml");
        assertThat(outputFile).exists();
        assertThat(Files.readString(outputFile, StandardCharsets.UTF_8))
                .contains("name: converter-suite")
                .contains("name: converter-test")
                .contains(ConverterTest.class.getName());
    }
}
