/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.stats;

import org.gradle.api.GradleException;

import javax.xml.XMLConstants;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/// §AR-test-harness.8: parses the JaCoCo XML report behind the stats and coverage-report tasks
/// into covered-line indexes and coverage metrics. It reads the report in one streaming pass and
/// keeps only the data it returns: the report of a large library such as `it.unimi.dsi:fastutil`
/// has a `<line>` element for every source line and does not fit a DOM in the daemon heap.
final class JacocoReportParser {

    /// The JDK charges every predefined entity reference in the report to these limits as if it
    /// expanded an entity named `[xml]`: a `&lt;` in text costs one, and an attribute value such
    /// as the `&lt;init&gt;` of each constructor costs three. Since JDK 24 both limits default to
    /// 100,000, so on JDK 25 a report with 33,334 constructors fails with `JAXP00010003` without
    /// declaring a single entity
    /// ([JDK-8359432](https://bugs.openjdk.org/browse/JDK-8359432)). With DTD support off no
    /// entity can be declared or expanded, so the limits are lifted; drop them once the JDK the
    /// harness runs on counts only declared entities.
    private static final String MAX_GENERAL_ENTITY_SIZE_LIMIT = "jdk.xml.maxGeneralEntitySizeLimit";
    private static final String TOTAL_ENTITY_SIZE_LIMIT = "jdk.xml.totalEntitySizeLimit";
    private static final String NO_LIMIT = "0";

    private final XMLStreamReader reader;
    private final Map<String, Set<Integer>> coveredLinesBySource = new HashMap<>();
    private final Map<String, Set<String>> methodNamesByClass = new HashMap<>();
    private final Map<String, LibraryStatsModels.CoverageMetric> rootCounters = new LinkedHashMap<>();
    private boolean hasClassElements;

    private JacocoReportParser(XMLStreamReader reader) {
        this.reader = reader;
    }

    static ParsedJacocoReport parse(Path jacocoReport) {
        if (!Files.isRegularFile(jacocoReport)) {
            throw new GradleException("Missing JaCoCo report " + jacocoReport);
        }

        try (InputStream input = new BufferedInputStream(Files.newInputStream(jacocoReport))) {
            XMLStreamReader reader = newInputFactory().createXMLStreamReader(input);
            try {
                return new JacocoReportParser(reader).readReport();
            } finally {
                reader.close();
            }
        } catch (IOException | XMLStreamException e) {
            throw new GradleException("Failed to parse JaCoCo report " + jacocoReport, e);
        }
    }

    /// The JDK's own StAX implementation, so the hardening below does not depend on which
    /// implementation the classpath provides. The report's `DOCTYPE` names a `report.dtd` that is
    /// never beside it: with DTD support off, the reader skips the DTD instead of resolving it.
    private static XMLInputFactory newInputFactory() {
        XMLInputFactory factory = XMLInputFactory.newDefaultFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setProperty(MAX_GENERAL_ENTITY_SIZE_LIMIT, NO_LIMIT);
        factory.setProperty(TOTAL_ENTITY_SIZE_LIMIT, NO_LIMIT);
        return factory;
    }

    /// Reads `<package>` and `<counter>` children of the root `<report>`, `<class>` and
    /// `<sourcefile>` children of a package, `<method>` children of a class, and `<line>` children
    /// of a source file. Every other element, such as a class's own counters, is skipped whole.
    private ParsedJacocoReport readReport() throws XMLStreamException {
        if (!nextChild() || !"report".equals(reader.getLocalName())) {
            throw new XMLStreamException("The root element is not <report>", reader.getLocation());
        }
        while (nextChild()) {
            switch (reader.getLocalName()) {
                case "package" -> readPackage();
                case "counter" -> readRootCounter();
                default -> skipElement();
            }
        }
        // Reading on to the end rejects whatever follows the root but is not well-formed.
        while (reader.hasNext()) {
            reader.next();
        }

        if (hasNothingToCover()) {
            LibraryStatsModels.CoverageMetricValue fullyCoveredMetric = fullyCoveredCoverageMetricValue();
            return new ParsedJacocoReport(
                    coveredLinesBySource,
                    methodNamesByClass,
                    fullyCoveredMetric,
                    fullyCoveredMetric,
                    fullyCoveredMetric
            );
        }
        return new ParsedJacocoReport(
                coveredLinesBySource,
                methodNamesByClass,
                coverageMetricOrNa("LINE"),
                coverageMetricOrNa("INSTRUCTION"),
                coverageMetricOrNa("METHOD")
        );
    }

    private void readPackage() throws XMLStreamException {
        String packageName = requiredAttribute("name");
        while (nextChild()) {
            switch (reader.getLocalName()) {
                case "class" -> readClass();
                case "sourcefile" -> readSourceFile(packageName);
                default -> skipElement();
            }
        }
    }

    /// Records the names of the methods JaCoCo kept in the class: a method whose every instruction
    /// a filter removed, such as a synthetic one, has no `<method name=… desc=… line=…>` element.
    private void readClass() throws XMLStreamException {
        hasClassElements = true;
        Set<String> methodNames = new LinkedHashSet<>();
        methodNamesByClass.put(requiredAttribute("name").replace('/', '.'), methodNames);
        while (nextChild()) {
            if ("method".equals(reader.getLocalName())) {
                methodNames.add(requiredAttribute("name"));
            }
            skipElement();
        }
    }

    private void readSourceFile(String packageName) throws XMLStreamException {
        String sourceName = requiredAttribute("name");
        String sourceKey = packageName.isEmpty() ? sourceName : packageName + "/" + sourceName;
        Set<Integer> coveredLines = new LinkedHashSet<>();
        coveredLinesBySource.put(sourceKey, coveredLines);
        while (nextChild()) {
            if ("line".equals(reader.getLocalName())) {
                int lineNumber = intAttribute("nr");
                if (intAttribute("ci") > 0) {
                    coveredLines.add(lineNumber);
                }
            }
            skipElement();
        }
    }

    private void readRootCounter() throws XMLStreamException {
        String type = requiredAttribute("type");
        long missed = longAttribute("missed");
        long covered = longAttribute("covered");
        long total = missed + covered;
        rootCounters.put(type, new LibraryStatsModels.CoverageMetric(
                covered,
                missed,
                total,
                LibraryStatsSupport.ratio(covered, total)
        ));
        skipElement();
    }

    /// Advances to the next child element of the current element and returns true, or returns
    /// false on the current element's end tag. Text, comments, and the DTD are passed over.
    private boolean nextChild() throws XMLStreamException {
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                return true;
            }
            if (event == XMLStreamConstants.END_ELEMENT) {
                return false;
            }
        }
        throw new XMLStreamException("The report ends inside an element", reader.getLocation());
    }

    /// Advances from the current start tag past its matching end tag.
    private void skipElement() throws XMLStreamException {
        int depth = 1;
        while (depth > 0) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                depth++;
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                depth--;
            }
        }
    }

    private String requiredAttribute(String name) throws XMLStreamException {
        String value = reader.getAttributeValue(null, name);
        if (value == null) {
            throw new XMLStreamException(
                    "<" + reader.getLocalName() + "> has no " + name + " attribute",
                    reader.getLocation()
            );
        }
        return value;
    }

    private int intAttribute(String name) throws XMLStreamException {
        String value = requiredAttribute(name);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new XMLStreamException(nonNumericAttribute(name, value), reader.getLocation(), e);
        }
    }

    private long longAttribute(String name) throws XMLStreamException {
        String value = requiredAttribute(name);
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new XMLStreamException(nonNumericAttribute(name, value), reader.getLocation(), e);
        }
    }

    private String nonNumericAttribute(String name, String value) {
        return "<" + reader.getLocalName() + "> has a non-numeric " + name + " attribute '" + value + "'";
    }

    private boolean hasNothingToCover() {
        LibraryStatsModels.CoverageMetric method = rootCounters.get("METHOD");
        if (method != null) {
            return method.total() == 0L;
        }
        return !hasClassElements;
    }

    private LibraryStatsModels.CoverageMetricValue coverageMetricOrNa(String type) {
        LibraryStatsModels.CoverageMetric coverageMetric = rootCounters.get(type);
        if (coverageMetric == null) {
            return LibraryStatsModels.CoverageMetricValue.notAvailable();
        }
        return LibraryStatsModels.CoverageMetricValue.available(coverageMetric);
    }

    private static LibraryStatsModels.CoverageMetricValue fullyCoveredCoverageMetricValue() {
        return LibraryStatsModels.CoverageMetricValue.available(new LibraryStatsModels.CoverageMetric(
                0L,
                0L,
                0L,
                LibraryStatsSupport.fullyCoveredRatio()
        ));
    }

    /// §AR-test-harness.8.1: `methodNamesByClass` maps each class JaCoCo analyzed, by binary name,
    /// to the names of its methods. A method JaCoCo filtered out of an analyzed class, such as a
    /// synthetic `class$` helper, is missing from its class's set even when another method's code
    /// shares its line; a class JaCoCo never analyzed has no entry.
    record ParsedJacocoReport(
            Map<String, Set<Integer>> coveredLinesBySource,
            Map<String, Set<String>> methodNamesByClass,
            LibraryStatsModels.CoverageMetricValue line,
            LibraryStatsModels.CoverageMetricValue instruction,
            LibraryStatsModels.CoverageMetricValue method
    ) {
    }
}
