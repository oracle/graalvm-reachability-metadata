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
import org.testng.TestNG;

public class MainTest {
    @TempDir
    Path outputDirectory;

    @Test
    void generatesJQueryHtmlReportWithBundledResources() throws Exception {
        SampleTest.invocations = 0;
        TestNG testNG = new TestNG(true);
        testNG.setVerbose(0);
        testNG.setOutputDirectory(outputDirectory.toString());
        testNG.setTestClasses(new Class[] {SampleTest.class});

        testNG.run();

        assertThat(testNG.getStatus()).isZero();
        assertThat(SampleTest.invocations).isEqualTo(1);
        assertThat(Files.readString(outputDirectory.resolve("index.html"), StandardCharsets.UTF_8))
                .contains("testng-reports.css")
                .contains("main-panel-root")
                .contains("SampleTest");
        assertThat(outputDirectory)
                .isDirectoryContaining(path -> path.getFileName().toString().equals("jquery-1.7.1.min.js"))
                .isDirectoryContaining(path -> path.getFileName().toString().equals("testng-reports.css"))
                .isDirectoryContaining(path -> path.getFileName().toString().equals("testng-reports.js"));
    }

    public static final class SampleTest {
        private static int invocations;

        @org.testng.annotations.Test
        public void passingTest() {
            invocations++;
        }
    }
}
