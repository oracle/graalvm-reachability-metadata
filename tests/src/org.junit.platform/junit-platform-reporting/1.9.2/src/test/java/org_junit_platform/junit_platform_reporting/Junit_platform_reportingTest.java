/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_junit_platform.junit_platform_reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
import static org.junit.platform.launcher.EngineFilter.includeEngines;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherConfig;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.reporting.legacy.xml.LegacyXmlReportGeneratingListener;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

class Junit_platform_reportingTest {
    @TempDir
    Path reportsDir;

    @Test
    void legacyXmlReportListenerWritesReport() throws Exception {
        StringWriter output = new StringWriter();
        LegacyXmlReportGeneratingListener reportListener =
                new LegacyXmlReportGeneratingListener(reportsDir, new PrintWriter(output));
        LauncherConfig launcherConfig = LauncherConfig.builder()
                .enableLauncherSessionListenerAutoRegistration(false)
                .enableLauncherDiscoveryListenerAutoRegistration(false)
                .enableTestExecutionListenerAutoRegistration(false)
                .enablePostDiscoveryFilterAutoRegistration(false)
                .enableTestEngineAutoRegistration(true)
                .addTestExecutionListeners(reportListener)
                .build();
        Launcher launcher = LauncherFactory.create(launcherConfig);
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(selectClass(SampleTestCase.class))
                .filters(includeEngines("junit-jupiter"))
                .build();

        launcher.execute(request);

        Path report = reportsDir.resolve("TEST-junit-jupiter.xml");
        assertThat(report).exists();
        assertThat(Files.readString(report)).contains("sampleTest");
        try (Stream<Path> reports = Files.list(reportsDir)) {
            List<Path> reportFiles = reports.toList();
            assertThat(reportFiles).containsExactly(report);
        }
        assertThat(output.toString()).isEmpty();
    }

    static class SampleTestCase {
        @Test
        void sampleTest() {
            assertThat("junit").startsWith("jun");
        }
    }
}
