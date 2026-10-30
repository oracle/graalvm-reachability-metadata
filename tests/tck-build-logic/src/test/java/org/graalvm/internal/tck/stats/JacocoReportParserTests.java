/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.stats;

import org.gradle.api.GradleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.xml.stream.XMLStreamException;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// §AR-test-harness.8: the JaCoCo report parser reads a report in one pass, skips its DTD, and
/// never resolves an entity.
class JacocoReportParserTests {

    /// The prolog JaCoCo writes: its `DOCTYPE` names a `report.dtd` that is never beside the report.
    private static final String JACOCO_PROLOG = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>\
            <!DOCTYPE report PUBLIC "-//JACOCO//DTD Report 1.1//EN" "report.dtd">""";

    /// Large enough that its `&lt;` and `&gt;` references exceed the JDK's 100,000 entity-size
    /// limits (JDK-8359432), which a DOM parse of the same report hits too.
    private static final int ENTITY_HEAVY_CLASSES = 40_000;

    @TempDir
    Path tempDir;

    /// Only the root's own counters are read: every nested element below counts instructions, but
    /// the root does not, so instruction coverage is not available. A `<group>`, which the
    /// harness's reports never have, is skipped with the packages inside it.
    @Test
    void readsEveryTopLevelPackageAndOnlyTheRootCounters() throws IOException {
        Path report = write("""
                <report name="demo">
                  <sessioninfo id="host-1" start="1" dump="2"/>
                  <package name="">
                    <class name="Main" sourcefilename="Main.java">
                      <method name="&lt;init&gt;" desc="()V" line="1">
                        <counter type="INSTRUCTION" missed="0" covered="3"/>
                        <counter type="METHOD" missed="0" covered="1"/>
                      </method>
                      <counter type="INSTRUCTION" missed="0" covered="3"/>
                      <counter type="METHOD" missed="0" covered="1"/>
                    </class>
                    <sourcefile name="Main.java">
                      <line nr="1" mi="0" ci="3" mb="0" cb="0"/>
                      <counter type="INSTRUCTION" missed="0" covered="3"/>
                      <counter type="LINE" missed="0" covered="1"/>
                    </sourcefile>
                    <counter type="INSTRUCTION" missed="0" covered="3"/>
                  </package>
                  <package name="com/example/a">
                    <class name="com/example/a/Foo$Inner" sourcefilename="Foo.java">
                      <method name="load" desc="()V" line="10"/>
                      <method name="lambda$load$0" desc="()V" line="12"/>
                    </class>
                    <class name="com/example/a/Empty" sourcefilename="Empty.java"/>
                    <sourcefile name="Foo.java">
                      <line nr="10" mi="0" ci="2" mb="0" cb="0"/>
                      <line nr="11" mi="4" ci="0" mb="0" cb="0"/>
                      <line nr="12" mi="1" ci="1" mb="0" cb="0"/>
                    </sourcefile>
                    <sourcefile name="Empty.java">
                      <line nr="3" mi="2" ci="0" mb="0" cb="0"/>
                    </sourcefile>
                    <counter type="INSTRUCTION" missed="99" covered="99"/>
                    <counter type="METHOD" missed="99" covered="99"/>
                  </package>
                  <package name="com/example/b">
                    <sourcefile name="Bar.java">
                      <line nr="20" mi="0" ci="5" mb="0" cb="0"/>
                    </sourcefile>
                  </package>
                  <group name="other">
                    <package name="com/example/grouped">
                      <class name="com/example/grouped/Baz" sourcefilename="Baz.java">
                        <method name="run" desc="()V" line="5"/>
                      </class>
                      <sourcefile name="Baz.java">
                        <line nr="5" mi="0" ci="2" mb="0" cb="0"/>
                      </sourcefile>
                    </package>
                    <counter type="INSTRUCTION" missed="0" covered="2"/>
                  </group>
                  <counter type="LINE" missed="2" covered="4"/>
                  <counter type="METHOD" missed="0" covered="3"/>
                </report>
                """);

        JacocoReportParser.ParsedJacocoReport parsed = JacocoReportParser.parse(report);

        assertThat(parsed.coveredLinesBySource()).isEqualTo(Map.of(
                "Main.java", Set.of(1),
                "com/example/a/Foo.java", Set.of(10, 12),
                "com/example/a/Empty.java", Set.of(),
                "com/example/b/Bar.java", Set.of(20)
        ));
        assertThat(parsed.methodNamesByClass()).isEqualTo(Map.of(
                "Main", Set.of("<init>"),
                "com.example.a.Foo$Inner", Set.of("load", "lambda$load$0"),
                "com.example.a.Empty", Set.of()
        ));
        assertThat(parsed.line().covered()).isEqualTo(4);
        assertThat(parsed.line().missed()).isEqualTo(2);
        assertThat(parsed.instruction().isAvailable())
                .as("nested INSTRUCTION counters never stand in for the root's")
                .isFalse();
        assertThat(parsed.method().covered()).isEqualTo(3);
        assertThat(parsed.method().missed()).isZero();
    }

    /// Without a root `METHOD` counter, a report is fully covered only when it has no class at all;
    /// one with classes has no coverage to report.
    @Test
    void treatsAReportWithoutMethodCounterAsFullyCoveredOnlyWithoutClasses() throws IOException {
        Path withClass = write("""
                <report name="demo"><package name="com/example">\
                <class name="com/example/Foo" sourcefilename="Foo.java"/></package></report>""");
        Path withoutClass = write("""
                <report name="demo"><package name="com/example"/></report>""");

        JacocoReportParser.ParsedJacocoReport parsedWithClass = JacocoReportParser.parse(withClass);
        JacocoReportParser.ParsedJacocoReport parsedWithoutClass = JacocoReportParser.parse(withoutClass);

        assertThat(parsedWithClass.methodNamesByClass()).isEqualTo(Map.of("com.example.Foo", Set.of()));
        assertThat(parsedWithClass.line().isAvailable()).isFalse();
        assertThat(parsedWithClass.instruction().isAvailable()).isFalse();
        assertThat(parsedWithClass.method().isAvailable()).isFalse();
        assertThat(parsedWithoutClass.method().isAvailable()).isTrue();
        assertThat(parsedWithoutClass.method().total()).isZero();
        assertThat(parsedWithoutClass.method().ratio()).isEqualByComparingTo(LibraryStatsSupport.fullyCoveredRatio());
    }

    @Test
    void skipsTheUnresolvableJacocoDoctype() throws IOException {
        Path report = write("""
                <report name="demo"><package name="com/example"><sourcefile name="Foo.java">\
                <line nr="10" mi="0" ci="3" mb="0" cb="0"/></sourcefile></package>\
                <counter type="LINE" missed="0" covered="1"/><counter type="METHOD" missed="0" covered="1"/>\
                </report>""");
        assertThat(report.resolveSibling("report.dtd")).doesNotExist();

        JacocoReportParser.ParsedJacocoReport parsed = JacocoReportParser.parse(report);

        assertThat(parsed.coveredLinesBySource()).isEqualTo(Map.of("com/example/Foo.java", Set.of(10)));
        assertThat(parsed.line().covered()).isEqualTo(1);
    }

    @Test
    void neverResolvesAnExternalEntity() throws IOException {
        Path leakedMarkup = tempDir.resolve("leak.xml");
        Files.writeString(leakedMarkup, """
                <sourcefile name="Leaked.java"><line nr="1" mi="0" ci="1" mb="0" cb="0"/></sourcefile>""");
        Path report = tempDir.resolve("jacoco.xml");
        Files.writeString(report, """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE report [<!ENTITY leak SYSTEM "%s">]>
                <report name="demo"><package name="com/example">&leak;</package></report>
                """.formatted(leakedMarkup.toUri()), StandardCharsets.UTF_8);

        assertThatThrownBy(() -> JacocoReportParser.parse(report))
                .isInstanceOf(GradleException.class)
                .hasMessage("Failed to parse JaCoCo report " + report)
                .rootCause()
                .isInstanceOf(XMLStreamException.class)
                .hasMessageContaining("\"leak\"");
    }

    @Test
    void readsMethodNamesPastTheJdkEntitySizeLimits() throws IOException {
        Path report = tempDir.resolve("jacoco.xml");
        try (BufferedWriter writer = Files.newBufferedWriter(report, StandardCharsets.UTF_8)) {
            writer.write(JACOCO_PROLOG + "<report name=\"demo\"><package name=\"com/example\">");
            for (int i = 0; i < ENTITY_HEAVY_CLASSES; i++) {
                writer.write("<class name=\"com/example/C" + i + "\" sourcefilename=\"C" + i + ".java\">"
                        + "<method name=\"&lt;clinit&gt;\" desc=\"()V\" line=\"1\"/>"
                        + "<method name=\"&lt;init&gt;\" desc=\"()V\" line=\"2\"/></class>");
            }
            writer.write("</package><counter type=\"METHOD\" missed=\"0\" covered=\""
                    + 2 * ENTITY_HEAVY_CLASSES + "\"/></report>");
        }

        JacocoReportParser.ParsedJacocoReport parsed = JacocoReportParser.parse(report);

        assertThat(parsed.methodNamesByClass()).hasSize(ENTITY_HEAVY_CLASSES);
        assertThat(parsed.methodNamesByClass().get("com.example.C" + (ENTITY_HEAVY_CLASSES - 1)))
                .containsExactly("<clinit>", "<init>");
    }

    @Test
    void failsLoudlyOnAMalformedReport() throws IOException {
        Path truncated = write("<report name=\"demo\"><package name=\"com/example\">");
        Path missingAttribute = write("""
                <report name="demo"><package name="com/example"><sourcefile name="Foo.java">\
                <line nr="10" mi="0" mb="0" cb="0"/></sourcefile></package></report>""");
        Path otherRoot = write("<coverage/>");

        assertThatThrownBy(() -> JacocoReportParser.parse(truncated))
                .isInstanceOf(GradleException.class)
                .hasRootCauseInstanceOf(XMLStreamException.class);
        assertThatThrownBy(() -> JacocoReportParser.parse(missingAttribute))
                .isInstanceOf(GradleException.class)
                .rootCause()
                .hasMessageContaining("<line> has no ci attribute");
        assertThatThrownBy(() -> JacocoReportParser.parse(otherRoot))
                .isInstanceOf(GradleException.class)
                .rootCause()
                .hasMessageContaining("The root element is not <report>");
    }

    /// Writes `body` after the JaCoCo prolog to a fresh report file.
    private Path write(String body) throws IOException {
        Path report = Files.createTempFile(tempDir, "jacoco", ".xml");
        Files.writeString(report, JACOCO_PROLOG + body, StandardCharsets.UTF_8);
        return report;
    }
}
