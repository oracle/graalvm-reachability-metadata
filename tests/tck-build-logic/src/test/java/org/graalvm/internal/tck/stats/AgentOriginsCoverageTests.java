/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.stats;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.graalvm.internal.tck.stats.CoverageMeasurementTests.createLibraryJar;

/// §AR-test-harness.8.1: call sites line matching cannot decide, because their frame is
/// line-less or JaCoCo filtered their method, are covered by agent origins.
class AgentOriginsCoverageTests {

    private static final String FOR_NAME = "java.lang.Class#forName(java.lang.String)";

    @TempDir
    Path tempDir;

    @Test
    void agentOriginsMatchDelegatedApiToNearestReportedCaller() throws IOException {
        Path libraryJar = createLibraryJar(tempDir.resolve("demo.jar"), List.of("com/example/Bar.class"));
        Path dynamicAccessDir = tempDir.resolve("dynamic-access");
        Files.createDirectories(dynamicAccessDir.resolve("demo"));
        Files.writeString(
                dynamicAccessDir.resolve("demo").resolve("resource-calls.json"),
                """
                {
                  "java.lang.Class#getResource(java.lang.String)": [
                    "com.example.Bar.outer(Unknown Source)",
                    "com.example.Bar.lookup(Unknown Source)"
                  ],
                  "java.lang.ClassLoader#getResource(java.lang.String)": [
                    "com.example.Bar.lookup(Unknown Source)"
                  ]
                }
                """,
                StandardCharsets.UTF_8
        );

        Path originsFile = tempDir.resolve("reachability-metadata-origins.txt");
        Files.writeString(
                originsFile,
                """
                {
                  "resources": root
                  └── com.example.Bar#outer()
                      └── com.example.Bar#lookup()
                          └── java.lang.Class#getResource(java.lang.String)
                              └── java.lang.ClassLoader#getResource(java.lang.String)
                                  └── jdk.internal.loader.BuiltinClassLoader#findResource(java.lang.String,java.lang.String) - [{"glob":"demo"}]
                }
                """,
                StandardCharsets.UTF_8
        );

        Set<CoverageMeasurement.AgentCoveredCallSite> coveredCallSites = CoverageMeasurement.parseAgentOrigins(originsFile, dynamicAccessDir);

        assertThat(coveredCallSites).containsExactly(new CoverageMeasurement.AgentCoveredCallSite(
                "java.lang.Class#getResource(java.lang.String)",
                "com.example.Bar",
                "lookup"
        ));

        LibraryStatsModels.DynamicAccessCoverageReport report = CoverageMeasurement.buildDynamicAccessCoverageReport(
                "com.example:demo:1.0.0",
                List.of(libraryJar),
                dynamicAccessDir,
                createJacocoReportWithoutLines(tempDir.resolve("jacoco.xml")),
                coveredCallSites
        );

        assertThat(report.totals().totalCalls()).isEqualTo(3);
        assertThat(report.totals().coveredCalls()).isEqualTo(1);
        assertThat(report.classes().getFirst().callSites())
                .filteredOn(LibraryStatsModels.DynamicAccessCallSiteCoverage::covered)
                .extracting(LibraryStatsModels.DynamicAccessCallSiteCoverage::trackedApi)
                .containsExactly("java.lang.Class#getResource(java.lang.String)");
        assertThat(report.classes().getFirst().callSites())
                .filteredOn(LibraryStatsModels.DynamicAccessCallSiteCoverage::covered)
                .extracting(LibraryStatsModels.DynamicAccessCallSiteCoverage::frame)
                .containsExactly("com.example.Bar.lookup(Unknown Source)");
    }

    @Test
    void agentOriginsNormalizeTrackedApiParameterSpacing() throws IOException {
        Path dynamicAccessDir = tempDir.resolve("dynamic-access");
        Files.createDirectories(dynamicAccessDir.resolve("demo"));
        Files.writeString(
                dynamicAccessDir.resolve("demo").resolve("reflection-calls.json"),
                """
                {
                  "java.lang.reflect.Method#invoke(java.lang.Object, java.lang.Object[])": [
                    "com.example.Foo.invoke(Unknown Source)"
                  ]
                }
                """,
                StandardCharsets.UTF_8
        );
        Path originsFile = tempDir.resolve("reachability-metadata-origins.txt");
        Files.writeString(
                originsFile,
                """
                {
                  "reflection": root
                  └── com.example.Foo#invoke()
                      └── java.lang.reflect.Method#invoke(java.lang.Object,java.lang.Object[]) - [{"type":"com.example.Target"}]
                }
                """,
                StandardCharsets.UTF_8
        );

        Set<CoverageMeasurement.AgentCoveredCallSite> coveredCallSites = CoverageMeasurement.parseAgentOrigins(originsFile, dynamicAccessDir);

        assertThat(coveredCallSites).containsExactly(new CoverageMeasurement.AgentCoveredCallSite(
                "java.lang.reflect.Method#invoke(java.lang.Object, java.lang.Object[])",
                "com.example.Foo",
                "invoke"
        ));
    }

    @Test
    void agentOriginsCoverSyntheticClassHelpersJacocoFilters() throws IOException {
        Path libraryJar = createJspApiJar();
        Path dynamicAccessDir = writeJspApiClassHelperCalls();
        Path jacocoReport = writeJspApiJacocoReport();
        Path originsFile = tempDir.resolve("reachability-metadata-origins.txt");
        Files.writeString(
                originsFile,
                """
                {
                  "reflection": root
                  └── org.example.TagSupportTest#findsAncestors()
                      ├── javax.servlet.jsp.tagext.SimpleTagSupport#findAncestorWithClass(javax.servlet.jsp.tagext.JspTag,java.lang.Class)
                      │   └── javax.servlet.jsp.tagext.SimpleTagSupport#class$(java.lang.String)
                      │       └── java.lang.Class#forName(java.lang.String) - [{"type":"javax.servlet.jsp.tagext.JspTag"}]
                      └── javax.servlet.jsp.tagext.TagSupport#findAncestorWithClass(javax.servlet.jsp.tagext.Tag,java.lang.Class)
                          └── javax.servlet.jsp.tagext.TagSupport#class$(java.lang.String)
                              └── java.lang.Class#forName(java.lang.String) - [{"type":"javax.servlet.jsp.tagext.Tag"}]
                }
                """,
                StandardCharsets.UTF_8
        );

        Set<CoverageMeasurement.AgentCoveredCallSite> coveredCallSites = CoverageMeasurement.parseAgentOrigins(originsFile, dynamicAccessDir);

        assertThat(coveredCallSites).containsExactlyInAnyOrder(
                new CoverageMeasurement.AgentCoveredCallSite(FOR_NAME, "javax.servlet.jsp.tagext.SimpleTagSupport", "class$"),
                new CoverageMeasurement.AgentCoveredCallSite(FOR_NAME, "javax.servlet.jsp.tagext.TagSupport", "class$")
        );
        LibraryStatsModels.DynamicAccessCoverageReport report = CoverageMeasurement.buildDynamicAccessCoverageReport(
                "jspapi:jsp-api:2.0-20040521",
                List.of(libraryJar),
                dynamicAccessDir,
                jacocoReport,
                coveredCallSites
        );
        assertThat(report.totals().totalCalls()).isEqualTo(2);
        assertThat(report.totals().coveredCalls()).isEqualTo(2);
        LibraryStatsModels.VersionStats versionStats = CoverageMeasurement.buildVersionStats(
                "jspapi:jsp-api:2.0-20040521",
                List.of(libraryJar),
                dynamicAccessDir,
                jacocoReport,
                coveredCallSites
        );
        assertThat(versionStats.dynamicAccess().totalCalls()).isEqualTo(2);
        assertThat(versionStats.dynamicAccess().coveredCalls()).isEqualTo(2);
    }

    @Test
    void syntheticClassHelpersWithoutAgentOriginsStayUncoveredAndRequestOrigins() throws IOException {
        Path libraryJar = createJspApiJar();
        Path dynamicAccessDir = writeJspApiClassHelperCalls();
        Path jacocoReport = writeJspApiJacocoReport();

        LibraryStatsModels.DynamicAccessCoverageReport report = CoverageMeasurement.buildDynamicAccessCoverageReport(
                "jspapi:jsp-api:2.0-20040521",
                List.of(libraryJar),
                dynamicAccessDir,
                jacocoReport,
                Set.of()
        );

        assertThat(report.totals().totalCalls()).isEqualTo(2);
        assertThat(report.totals().coveredCalls()).isZero();
        assertThat(CoverageMeasurement.lineMatchingUndecided(dynamicAccessDir, List.of(libraryJar), jacocoReport)).isTrue();
    }

    @Test
    void agentOriginsAreRequestedOnlyWhenLineMatchingCannotDecideASite() throws IOException {
        Path libraryJar = createLibraryJar(tempDir.resolve("demo.jar"), List.of("com/example/Foo.class", "com/example/Bar.class"));
        Path dynamicAccessDir = tempDir.resolve("dynamic-access");
        Files.createDirectories(dynamicAccessDir.resolve("demo"));
        Path reflectionCalls = dynamicAccessDir.resolve("demo").resolve("reflection-calls.json");
        Files.writeString(
                reflectionCalls,
                """
                {
                  "java.lang.Class#forName(java.lang.String)": [
                    "com.example.Foo.load(Foo.java:10)",
                    "com.example.Foo.check(Foo.java:11)"
                  ]
                }
                """,
                StandardCharsets.UTF_8
        );
        Path jacocoReport = tempDir.resolve("jacoco.xml");
        Files.writeString(
                jacocoReport,
                """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <report name="demo">
                  <package name="com/example">
                    <class name="com/example/Foo" sourcefilename="Foo.java">
                      <method name="load" desc="()V" line="10"/>
                      <method name="check" desc="()V" line="11"/>
                    </class>
                    <sourcefile name="Foo.java">
                      <line nr="10" mi="0" ci="3" mb="0" cb="0"/>
                      <line nr="11" mi="2" ci="0" mb="0" cb="0"/>
                    </sourcefile>
                  </package>
                  <counter type="INSTRUCTION" missed="2" covered="3"/>
                  <counter type="LINE" missed="1" covered="1"/>
                  <counter type="METHOD" missed="1" covered="1"/>
                </report>
                """,
                StandardCharsets.UTF_8
        );

        assertThat(CoverageMeasurement.lineMatchingUndecided(dynamicAccessDir, List.of(libraryJar), jacocoReport)).isFalse();

        Files.writeString(
                dynamicAccessDir.resolve("demo").resolve("resource-calls.json"),
                """
                {
                  "java.lang.Class#getResource(java.lang.String)": [
                    "com.example.Bar.lookup(Unknown Source)"
                  ]
                }
                """,
                StandardCharsets.UTF_8
        );

        assertThat(CoverageMeasurement.lineMatchingUndecided(dynamicAccessDir, List.of(libraryJar), jacocoReport)).isTrue();
    }

    private Path createJspApiJar() throws IOException {
        return createLibraryJar(tempDir.resolve("jsp-api.jar"), List.of(
                "javax/servlet/jsp/tagext/SimpleTagSupport.class",
                "javax/servlet/jsp/tagext/TagSupport.class"
        ));
    }

    /// The two call sites of `jspapi:jsp-api:2.0-20040521`: each sits in the synthetic `class$`
    /// helper that pre-Java 5 javac emits for a class literal.
    private Path writeJspApiClassHelperCalls() throws IOException {
        Path dynamicAccessDir = tempDir.resolve("dynamic-access");
        Files.createDirectories(dynamicAccessDir.resolve("jsp-api"));
        Files.writeString(
                dynamicAccessDir.resolve("jsp-api").resolve("reflection-calls.json"),
                """
                {
                  "java.lang.Class#forName(java.lang.String)": [
                    "javax.servlet.jsp.tagext.SimpleTagSupport.class$(SimpleTagSupport.java:183)",
                    "javax.servlet.jsp.tagext.TagSupport.class$(TagSupport.java:80)"
                  ]
                }
                """,
                StandardCharsets.UTF_8
        );
        return dynamicAccessDir;
    }

    /// As the real report has it: JaCoCo analyzed both classes and kept `findAncestorWithClass`,
    /// but filtered the synthetic `class$` methods, so lines 80 and 183 are never reported.
    private Path writeJspApiJacocoReport() throws IOException {
        Path jacocoReport = tempDir.resolve("jacoco.xml");
        Files.writeString(
                jacocoReport,
                """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <report name="jsp-api">
                  <package name="javax/servlet/jsp/tagext">
                    <class name="javax/servlet/jsp/tagext/SimpleTagSupport" sourcefilename="SimpleTagSupport.java">
                      <method name="findAncestorWithClass" desc="(Ljavax/servlet/jsp/tagext/JspTag;Ljava/lang/Class;)Ljavax/servlet/jsp/tagext/JspTag;" line="180"/>
                    </class>
                    <class name="javax/servlet/jsp/tagext/TagSupport" sourcefilename="TagSupport.java">
                      <method name="findAncestorWithClass" desc="(Ljavax/servlet/jsp/tagext/Tag;Ljava/lang/Class;)Ljavax/servlet/jsp/tagext/Tag;" line="76"/>
                    </class>
                    <sourcefile name="SimpleTagSupport.java">
                      <line nr="182" mi="0" ci="4" mb="0" cb="0"/>
                      <line nr="185" mi="0" ci="3" mb="0" cb="0"/>
                    </sourcefile>
                    <sourcefile name="TagSupport.java">
                      <line nr="78" mi="0" ci="4" mb="0" cb="0"/>
                      <line nr="82" mi="0" ci="3" mb="0" cb="0"/>
                    </sourcefile>
                  </package>
                  <counter type="INSTRUCTION" missed="0" covered="14"/>
                  <counter type="LINE" missed="0" covered="4"/>
                  <counter type="METHOD" missed="0" covered="2"/>
                </report>
                """,
                StandardCharsets.UTF_8
        );
        return jacocoReport;
    }

    private Path createJacocoReportWithoutLines(Path reportPath) throws IOException {
        Files.writeString(
                reportPath,
                """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <report name="demo">
                  <counter type="INSTRUCTION" missed="1" covered="1"/>
                  <counter type="METHOD" missed="1" covered="1"/>
                </report>
                """,
                StandardCharsets.UTF_8
        );
        return reportPath;
    }
}
