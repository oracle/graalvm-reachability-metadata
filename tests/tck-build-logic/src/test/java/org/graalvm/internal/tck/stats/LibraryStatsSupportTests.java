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
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LibraryStatsSupportTests {

    @TempDir
    Path tempDir;

    @Test
    void buildVersionStatsParsesDynamicAccessCoverageAndDeduplicatesCallSites() throws IOException {
        Path libraryJar = createLibraryJar(tempDir.resolve("demo.jar"), List.of(
                "com/example/Foo.class",
                "com/example/Bar.class"
        ));

        Path dynamicAccessDir = tempDir.resolve("dynamic-access");
        Files.createDirectories(dynamicAccessDir.resolve("demo"));
        Files.writeString(
                dynamicAccessDir.resolve("demo").resolve("reflection-calls.json"),
                """
                {
                  "java.lang.Class#forName(java.lang.String)": [
                    "com.example.Foo.load(Foo.java:10)",
                    "com.example.Foo.load(Foo.java:10)",
                    "com.example.Foo.noLine(Foo.java)",
                    "java.lang.String.valueOf(String.java:10)"
                  ]
                }
                """,
                StandardCharsets.UTF_8
        );
        Files.writeString(
                dynamicAccessDir.resolve("demo").resolve("resource-calls.json"),
                """
                {
                  "java.lang.Class#getResource(java.lang.String)": [
                    "com.example.Bar.lookup(Bar.java:20)"
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
                    <sourcefile name="Foo.java">
                      <line nr="10" mi="0" ci="3" mb="0" cb="0"/>
                    </sourcefile>
                    <sourcefile name="Bar.java">
                      <line nr="20" mi="1" ci="0" mb="0" cb="0"/>
                    </sourcefile>
                  </package>
                  <counter type="INSTRUCTION" missed="4" covered="6"/>
                  <counter type="LINE" missed="1" covered="1"/>
                  <counter type="METHOD" missed="2" covered="3"/>
                </report>
                """,
                StandardCharsets.UTF_8
        );

        LibraryStatsModels.VersionStats versionStats = LibraryStatsSupport.buildVersionStats(
                "com.example:demo:1.0.0",
                List.of(libraryJar),
                dynamicAccessDir,
                jacocoReport
        );

        assertThat(versionStats.version()).isEqualTo("1.0.0");
        assertThat(versionStats.dynamicAccess().totalCalls()).isEqualTo(3);
        assertThat(versionStats.dynamicAccess().coveredCalls()).isEqualTo(1);
        assertThat(versionStats.dynamicAccess().breakdown().keySet()).containsExactly("reflection", "resources");
        assertThat(versionStats.dynamicAccess().breakdown().get("reflection").totalCalls()).isEqualTo(2);
        assertThat(versionStats.dynamicAccess().breakdown().get("reflection").coveredCalls()).isEqualTo(1);
        assertThat(versionStats.dynamicAccess().breakdown().get("resources").totalCalls()).isEqualTo(1);
        assertThat(versionStats.dynamicAccess().breakdown().get("resources").coveredCalls()).isEqualTo(0);
        assertThat(versionStats.libraryCoverage().line().covered()).isEqualTo(1);
        assertThat(versionStats.libraryCoverage().line().missed()).isEqualTo(1);
        assertThat(versionStats.libraryCoverage().line().total()).isEqualTo(2);
        assertThat(versionStats.libraryCoverage().instruction().covered()).isEqualTo(6);
        assertThat(versionStats.libraryCoverage().instruction().missed()).isEqualTo(4);
        assertThat(versionStats.libraryCoverage().method().covered()).isEqualTo(3);
        assertThat(versionStats.libraryCoverage().method().missed()).isEqualTo(2);
    }

    @Test
    void buildVersionStatsAllowsMissingDynamicAccessDirectory() throws IOException {
        Path libraryJar = createLibraryJar(tempDir.resolve("demo.jar"), List.of("com/example/Foo.class"));

        Path missingDynamicAccessDir = tempDir.resolve("dynamic-access-missing");

        Path jacocoReport = tempDir.resolve("jacoco.xml");
        Files.writeString(
                jacocoReport,
                """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <report name="demo">
                  <counter type="INSTRUCTION" missed="1" covered="2"/>
                  <counter type="LINE" missed="1" covered="1"/>
                  <counter type="METHOD" missed="0" covered="1"/>
                </report>
                """,
                StandardCharsets.UTF_8
        );

        LibraryStatsModels.VersionStats versionStats = LibraryStatsSupport.buildVersionStats(
                "com.example:demo:1.0.0",
                List.of(libraryJar),
                missingDynamicAccessDir,
                jacocoReport
        );

        assertThat(versionStats.dynamicAccess().totalCalls()).isEqualTo(0);
        assertThat(versionStats.dynamicAccess().coveredCalls()).isEqualTo(0);
        assertThat(versionStats.dynamicAccess().breakdown()).isEmpty();
        assertThat(versionStats.dynamicAccess().coverageRatio()).isEqualByComparingTo("1");
    }

    @Test
    void buildVersionStatsTreatsLibrariesWithoutClassFilesAsEmptyDynamicAccess() throws IOException {
        Path libraryJar = createLibraryJar(tempDir.resolve("demo.jar"), List.of("META-INF/MANIFEST.MF"));

        LibraryStatsModels.VersionStats versionStats = LibraryStatsSupport.buildVersionStats(
                "com.example:demo:1.0.0",
                List.of(libraryJar),
                tempDir.resolve("dynamic-access-missing"),
                tempDir.resolve("jacoco-missing.xml")
        );

        assertThat(versionStats.dynamicAccess().totalCalls()).isZero();
        assertThat(versionStats.dynamicAccess().coveredCalls()).isZero();
        assertThat(versionStats.dynamicAccess().breakdown()).isEmpty();
        assertThat(versionStats.dynamicAccess().coverageRatio()).isEqualByComparingTo("1.0");
        assertThat(versionStats.libraryCoverage().line().isAvailable()).isFalse();
        assertThat(versionStats.libraryCoverage().instruction().isAvailable()).isFalse();
        assertThat(versionStats.libraryCoverage().method().isAvailable()).isFalse();
    }

    @Test
    void buildVersionStatsWithoutDynamicAccessPreservesCoverageAndMarksDynamicAccessAsUnavailable() throws IOException {
        Path jacocoReport = tempDir.resolve("jacoco.xml");
        Files.writeString(
                jacocoReport,
                """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <report name="demo">
                  <counter type="INSTRUCTION" missed="1" covered="2"/>
                  <counter type="LINE" missed="1" covered="1"/>
                  <counter type="METHOD" missed="0" covered="1"/>
                </report>
                """,
                StandardCharsets.UTF_8
        );

        LibraryStatsModels.VersionStats versionStats = LibraryStatsSupport.buildVersionStatsWithoutDynamicAccess(
                "com.example:demo:1.0.0",
                jacocoReport
        );

        assertThat(versionStats.version()).isEqualTo("1.0.0");
        assertThat(versionStats.dynamicAccess().isAvailable()).isFalse();
        assertThat(versionStats.libraryCoverage().line().covered()).isEqualTo(1);
        assertThat(versionStats.libraryCoverage().line().missed()).isEqualTo(1);
        assertThat(versionStats.libraryCoverage().instruction().covered()).isEqualTo(2);
        assertThat(versionStats.libraryCoverage().instruction().missed()).isEqualTo(1);
        assertThat(versionStats.libraryCoverage().method().covered()).isEqualTo(1);
        assertThat(versionStats.libraryCoverage().method().missed()).isEqualTo(0);
    }

    @Test
    void buildVersionStatsAllowsJacocoReportsWithoutLineCoverageData() throws IOException {
        Path libraryJar = createLibraryJar(tempDir.resolve("demo.jar"), List.of("com/example/Foo.class"));

        Path dynamicAccessDir = tempDir.resolve("dynamic-access");
        Files.createDirectories(dynamicAccessDir.resolve("demo"));
        Files.writeString(
                dynamicAccessDir.resolve("demo").resolve("reflection-calls.json"),
                """
                {
                  "java.lang.Class#forName(java.lang.String)": [
                    "com.example.Foo.load(Foo.java:10)"
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
                    <class name="com/example/Foo">
                      <method name="load" desc="()V">
                        <counter type="INSTRUCTION" missed="1" covered="2"/>
                        <counter type="METHOD" missed="0" covered="1"/>
                      </method>
                      <counter type="INSTRUCTION" missed="1" covered="2"/>
                      <counter type="METHOD" missed="0" covered="1"/>
                      <counter type="CLASS" missed="0" covered="1"/>
                    </class>
                  </package>
                  <counter type="INSTRUCTION" missed="1" covered="2"/>
                  <counter type="METHOD" missed="0" covered="1"/>
                </report>
                """,
                StandardCharsets.UTF_8
        );

        LibraryStatsModels.VersionStats versionStats = LibraryStatsSupport.buildVersionStats(
                "com.example:demo:1.0.0",
                List.of(libraryJar),
                dynamicAccessDir,
                jacocoReport
        );

        assertThat(versionStats.dynamicAccess().totalCalls()).isEqualTo(1);
        assertThat(versionStats.dynamicAccess().coveredCalls()).isEqualTo(0);
        assertThat(versionStats.dynamicAccess().coverageRatio()).isEqualByComparingTo("0");
        assertThat(versionStats.libraryCoverage().line().isAvailable()).isFalse();
        assertThat(versionStats.libraryCoverage().instruction().covered()).isEqualTo(2);
        assertThat(versionStats.libraryCoverage().instruction().missed()).isEqualTo(1);
        assertThat(versionStats.libraryCoverage().method().covered()).isEqualTo(1);
        assertThat(versionStats.libraryCoverage().method().missed()).isEqualTo(0);
    }

    @Test
    void buildVersionStatsTreatsLibrariesWithoutExecutableBytecodeAsFullyCovered() throws IOException {
        Path libraryJar = createLibraryJar(tempDir.resolve("demo.jar"), List.of("com/example/Marker.class"));

        Path jacocoReport = tempDir.resolve("jacoco.xml");
        Files.writeString(
                jacocoReport,
                """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <report name="demo">
                  <package name="com/example">
                    <class name="com/example/Marker"/>
                  </package>
                  <counter type="INSTRUCTION" missed="0" covered="0"/>
                  <counter type="METHOD" missed="0" covered="0"/>
                </report>
                """,
                StandardCharsets.UTF_8
        );

        LibraryStatsModels.VersionStats versionStats = LibraryStatsSupport.buildVersionStatsWithoutDynamicAccess(
                "com.example:demo:1.0.0",
                jacocoReport
        );

        assertThat(versionStats.libraryCoverage().line().isAvailable()).isTrue();
        assertThat(versionStats.libraryCoverage().line().covered()).isZero();
        assertThat(versionStats.libraryCoverage().line().missed()).isZero();
        assertThat(versionStats.libraryCoverage().line().total()).isZero();
        assertThat(versionStats.libraryCoverage().line().ratio()).isEqualByComparingTo("1.0");
        assertThat(versionStats.libraryCoverage().instruction().covered()).isZero();
        assertThat(versionStats.libraryCoverage().instruction().missed()).isZero();
        assertThat(versionStats.libraryCoverage().instruction().total()).isZero();
        assertThat(versionStats.libraryCoverage().instruction().ratio()).isEqualByComparingTo("1.0");
        assertThat(versionStats.libraryCoverage().method().covered()).isZero();
        assertThat(versionStats.libraryCoverage().method().missed()).isZero();
        assertThat(versionStats.libraryCoverage().method().total()).isZero();
        assertThat(versionStats.libraryCoverage().method().ratio()).isEqualByComparingTo("1.0");
    }

    @Test
    void buildDynamicAccessCoverageReportGroupsCallSitesByClassAndSortsByUncoveredCalls() throws IOException {
        Path libraryJar = createLibraryJar(tempDir.resolve("demo.jar"), List.of(
                "com/example/Foo.class",
                "com/example/Bar.class"
        ));

        Path dynamicAccessDir = tempDir.resolve("dynamic-access");
        Files.createDirectories(dynamicAccessDir.resolve("demo"));
        Files.writeString(
                dynamicAccessDir.resolve("demo").resolve("reflection-calls.json"),
                """
                {
                  "java.lang.Class#forName(java.lang.String)": [
                    "com.example.Foo.load(Foo.java:10)",
                    "com.example.Foo.load(Foo.java:10)",
                    "com.example.Foo.noLine(Foo.java)",
                    "com.example.Bar.load(Bar.java:20)"
                  ]
                }
                """,
                StandardCharsets.UTF_8
        );
        Files.writeString(
                dynamicAccessDir.resolve("demo").resolve("resource-calls.json"),
                """
                {
                  "java.lang.Class#getResource(java.lang.String)": [
                    "com.example.Bar.lookup(Bar.java:21)"
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
                    <sourcefile name="Foo.java">
                      <line nr="10" mi="0" ci="3" mb="0" cb="0"/>
                    </sourcefile>
                    <sourcefile name="Bar.java">
                      <line nr="20" mi="1" ci="0" mb="0" cb="0"/>
                      <line nr="21" mi="0" ci="2" mb="0" cb="0"/>
                    </sourcefile>
                  </package>
                  <counter type="INSTRUCTION" missed="4" covered="6"/>
                  <counter type="LINE" missed="1" covered="2"/>
                  <counter type="METHOD" missed="2" covered="3"/>
                </report>
                """,
                StandardCharsets.UTF_8
        );

        LibraryStatsModels.DynamicAccessCoverageReport report = LibraryStatsSupport.buildDynamicAccessCoverageReport(
                "com.example:demo:1.0.0",
                List.of(libraryJar),
                dynamicAccessDir,
                jacocoReport
        );

        assertThat(report.coordinate()).isEqualTo("com.example:demo:1.0.0");
        assertThat(report.hasDynamicAccess()).isTrue();
        assertThat(report.totals().totalCalls()).isEqualTo(4);
        assertThat(report.totals().coveredCalls()).isEqualTo(2);
        assertThat(report.classes()).extracting(LibraryStatsModels.DynamicAccessClassCoverage::className)
                .containsExactly("com.example.Bar", "com.example.Foo");
        assertThat(report.classes().get(0).sourceFile()).isEqualTo("Bar.java");
        assertThat(report.classes().get(0).totalCalls()).isEqualTo(2);
        assertThat(report.classes().get(0).coveredCalls()).isEqualTo(1);
        assertThat(report.classes().get(0).callSites())
                .extracting(LibraryStatsModels.DynamicAccessCallSiteCoverage::frame)
                .containsExactly(
                        "com.example.Bar.load(Bar.java:20)",
                        "com.example.Bar.lookup(Bar.java:21)"
                );
        assertThat(report.classes().get(0).callSites())
                .extracting(LibraryStatsModels.DynamicAccessCallSiteCoverage::covered)
                .containsExactly(false, true);
        assertThat(report.classes().get(1).totalCalls()).isEqualTo(2);
        assertThat(report.classes().get(1).coveredCalls()).isEqualTo(1);
    }

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

        Set<LibraryStatsSupport.AgentCoveredCallSite> coveredCallSites = LibraryStatsSupport.parseAgentOrigins(originsFile, dynamicAccessDir);

        assertThat(coveredCallSites).containsExactly(new LibraryStatsSupport.AgentCoveredCallSite(
                "java.lang.Class#getResource(java.lang.String)",
                "com.example.Bar",
                "lookup"
        ));

        LibraryStatsModels.DynamicAccessCoverageReport report = LibraryStatsSupport.buildDynamicAccessCoverageReport(
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

        Set<LibraryStatsSupport.AgentCoveredCallSite> coveredCallSites = LibraryStatsSupport.parseAgentOrigins(originsFile, dynamicAccessDir);

        assertThat(coveredCallSites).containsExactly(new LibraryStatsSupport.AgentCoveredCallSite(
                "java.lang.reflect.Method#invoke(java.lang.Object, java.lang.Object[])",
                "com.example.Foo",
                "invoke"
        ));
    }

    @Test
    void buildDynamicAccessCoverageReportReturnsEmptyClassesWhenDynamicAccessDirectoryIsMissing() throws IOException {
        Path libraryJar = createLibraryJar(tempDir.resolve("demo.jar"), List.of("com/example/Foo.class"));

        Path missingDynamicAccessDir = tempDir.resolve("dynamic-access-missing");

        Path jacocoReport = tempDir.resolve("jacoco.xml");
        Files.writeString(
                jacocoReport,
                """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <report name="demo">
                  <counter type="INSTRUCTION" missed="1" covered="2"/>
                  <counter type="LINE" missed="1" covered="1"/>
                  <counter type="METHOD" missed="0" covered="1"/>
                </report>
                """,
                StandardCharsets.UTF_8
        );

        LibraryStatsModels.DynamicAccessCoverageReport report = LibraryStatsSupport.buildDynamicAccessCoverageReport(
                "com.example:demo:1.0.0",
                List.of(libraryJar),
                missingDynamicAccessDir,
                jacocoReport
        );

        assertThat(report.hasDynamicAccess()).isFalse();
        assertThat(report.totals().totalCalls()).isEqualTo(0);
        assertThat(report.totals().coveredCalls()).isEqualTo(0);
        assertThat(report.classes()).isEmpty();
    }

    @Test
    void buildDynamicAccessCoverageReportAllowsLibrariesWithoutClassFiles() throws IOException {
        Path libraryJar = createLibraryJar(tempDir.resolve("demo.jar"), List.of("META-INF/MANIFEST.MF"));

        LibraryStatsModels.DynamicAccessCoverageReport report = LibraryStatsSupport.buildDynamicAccessCoverageReport(
                "com.example:demo:1.0.0",
                List.of(libraryJar),
                tempDir.resolve("dynamic-access-missing"),
                tempDir.resolve("jacoco-missing.xml")
        );

        assertThat(report.coordinate()).isEqualTo("com.example:demo:1.0.0");
        assertThat(report.hasDynamicAccess()).isFalse();
        assertThat(report.totals().totalCalls()).isZero();
        assertThat(report.totals().coveredCalls()).isZero();
        assertThat(report.classes()).isEmpty();
    }

    private Path createLibraryJar(Path jarPath, List<String> entries) throws IOException {
        try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(jarPath))) {
            for (String entry : entries) {
                jarOutputStream.putNextEntry(new JarEntry(entry));
                jarOutputStream.write(new byte[]{0});
                jarOutputStream.closeEntry();
            }
        }
        return jarPath;
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
