/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.stats;

import org.gradle.api.GradleException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Parses JaCoCo XML reports into covered-line indexes and coverage metrics.
 */
final class JacocoReportParser {

    private JacocoReportParser() {
    }

    static ParsedJacocoReport parse(Path jacocoReport) {
        if (!Files.isRegularFile(jacocoReport)) {
            throw new GradleException("Missing JaCoCo report " + jacocoReport);
        }

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            Document document = factory.newDocumentBuilder().parse(jacocoReport.toFile());
            Element root = document.getDocumentElement();

            Map<String, Set<Integer>> coveredLinesBySource = new HashMap<>();
            NodeList packages = root.getChildNodes();
            for (int i = 0; i < packages.getLength(); i++) {
                Node node = packages.item(i);
                if (!(node instanceof Element packageElement) || !"package".equals(packageElement.getTagName())) {
                    continue;
                }
                String packageName = packageElement.getAttribute("name");
                NodeList sourceFiles = packageElement.getChildNodes();
                for (int j = 0; j < sourceFiles.getLength(); j++) {
                    Node sourceNode = sourceFiles.item(j);
                    if (!(sourceNode instanceof Element sourceFileElement) || !"sourcefile".equals(sourceFileElement.getTagName())) {
                        continue;
                    }
                    String sourceName = sourceFileElement.getAttribute("name");
                    String sourceKey = packageName == null || packageName.isEmpty() ? sourceName : packageName + "/" + sourceName;
                    Set<Integer> coveredLines = new LinkedHashSet<>();
                    NodeList lineNodes = sourceFileElement.getChildNodes();
                    for (int k = 0; k < lineNodes.getLength(); k++) {
                        Node lineNode = lineNodes.item(k);
                        if (!(lineNode instanceof Element lineElement) || !"line".equals(lineElement.getTagName())) {
                            continue;
                        }
                        int coveredInstructions = Integer.parseInt(lineElement.getAttribute("ci"));
                        if (coveredInstructions > 0) {
                            coveredLines.add(Integer.parseInt(lineElement.getAttribute("nr")));
                        }
                    }
                    coveredLinesBySource.put(sourceKey, coveredLines);
                }
            }

            Map<String, LibraryStatsModels.CoverageMetric> rootCounters = parseRootCounters(root);
            if (hasNothingToCover(root, rootCounters)) {
                LibraryStatsModels.CoverageMetricValue fullyCoveredMetric = fullyCoveredCoverageMetricValue();
                return new ParsedJacocoReport(
                        coveredLinesBySource,
                        fullyCoveredMetric,
                        fullyCoveredMetric,
                        fullyCoveredMetric
                );
            }
            return new ParsedJacocoReport(
                    coveredLinesBySource,
                    coverageMetricOrNa(rootCounters, "LINE"),
                    coverageMetricOrNa(rootCounters, "INSTRUCTION"),
                    coverageMetricOrNa(rootCounters, "METHOD")
            );
        } catch (Exception e) {
            throw new GradleException("Failed to parse JaCoCo report " + jacocoReport, e);
        }
    }

    private static boolean hasNothingToCover(
            Element root,
            Map<String, LibraryStatsModels.CoverageMetric> rootCounters
    ) {
        LibraryStatsModels.CoverageMetric method = rootCounters.get("METHOD");
        if (method != null) {
            return method.total() == 0L;
        }
        return !hasAnyClassElements(root);
    }

    private static boolean hasAnyClassElements(Element root) {
        NodeList packages = root.getChildNodes();
        for (int i = 0; i < packages.getLength(); i++) {
            Node packageNode = packages.item(i);
            if (!(packageNode instanceof Element packageElement) || !"package".equals(packageElement.getTagName())) {
                continue;
            }
            NodeList packageChildren = packageElement.getChildNodes();
            for (int j = 0; j < packageChildren.getLength(); j++) {
                Node child = packageChildren.item(j);
                if (child instanceof Element childElement && "class".equals(childElement.getTagName())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Map<String, LibraryStatsModels.CoverageMetric> parseRootCounters(Element root) {
        Map<String, LibraryStatsModels.CoverageMetric> counters = new LinkedHashMap<>();
        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (!(node instanceof Element counterElement) || !"counter".equals(counterElement.getTagName())) {
                continue;
            }
            String type = counterElement.getAttribute("type");
            long missed = Long.parseLong(counterElement.getAttribute("missed"));
            long covered = Long.parseLong(counterElement.getAttribute("covered"));
            long total = missed + covered;
            counters.put(type, new LibraryStatsModels.CoverageMetric(covered, missed, total, LibraryStatsSupport.ratio(covered, total)));
        }
        return counters;
    }

    private static LibraryStatsModels.CoverageMetricValue coverageMetricOrNa(
            Map<String, LibraryStatsModels.CoverageMetric> counters,
            String type
    ) {
        LibraryStatsModels.CoverageMetric coverageMetric = counters.get(type);
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

    record ParsedJacocoReport(
            Map<String, Set<Integer>> coveredLinesBySource,
            LibraryStatsModels.CoverageMetricValue line,
            LibraryStatsModels.CoverageMetricValue instruction,
            LibraryStatsModels.CoverageMetricValue method
    ) {
    }
}
