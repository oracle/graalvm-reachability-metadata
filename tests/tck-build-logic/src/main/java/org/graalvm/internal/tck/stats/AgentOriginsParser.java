/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.stats;

import com.fasterxml.jackson.core.type.TypeReference;
import org.gradle.api.GradleException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Matches statically reported call sites against native-image-agent origins.
 */
final class AgentOriginsParser {

    private AgentOriginsParser() {
    }

    /// §AR-test-harness.8: streams the agent's compressed configuration-origin trees and
    /// retains the statically reported call sites whose exact tracked API and caller
    /// class/method occur, in that order, on a path carrying configuration. `originsOutput` may
    /// be one origins file or the agent output directory. Returns an empty set when no origins
    /// output or no reported call sites exist. Candidates are taken from the reports as-is:
    /// the reports only contain library call sites, and the coverage side only consults the
    /// result for line-less library frames.
    static Set<LibraryStatsSupport.AgentCoveredCallSite> parseAgentOrigins(Path originsOutput, Path dynamicAccessDir) {
        if (originsOutput == null || !Files.exists(originsOutput)) {
            return Set.of();
        }
        Set<LibraryStatsSupport.AgentCoveredCallSite> candidates = loadAgentOriginCandidates(dynamicAccessDir);
        if (candidates.isEmpty()) {
            return Set.of();
        }

        Map<String, List<LibraryStatsSupport.AgentCoveredCallSite>> candidatesByTrackedApi = new HashMap<>();
        for (LibraryStatsSupport.AgentCoveredCallSite candidate : candidates) {
            candidatesByTrackedApi
                    .computeIfAbsent(normalizeMethodSignature(candidate.trackedApi()), ignored -> new ArrayList<>())
                    .add(candidate);
        }

        Set<LibraryStatsSupport.AgentCoveredCallSite> coveredCallSites = new LinkedHashSet<>();
        for (Path originsFile : listAgentOriginsFiles(originsOutput)) {
            parseAgentOriginsFile(originsFile, candidatesByTrackedApi, candidates.size(), coveredCallSites);
            if (coveredCallSites.size() == candidates.size()) {
                break;
            }
        }
        return Set.copyOf(coveredCallSites);
    }

    private static Set<LibraryStatsSupport.AgentCoveredCallSite> loadAgentOriginCandidates(Path dynamicAccessDir) {
        if (!Files.isDirectory(dynamicAccessDir)) {
            return Set.of();
        }
        Set<LibraryStatsSupport.AgentCoveredCallSite> candidates = new LinkedHashSet<>();
        try (Stream<Path> files = Files.walk(dynamicAccessDir)) {
            for (Path reportFile : files
                    .filter(Files::isRegularFile)
                    .filter(path -> DynamicAccessReportsParser.DYNAMIC_ACCESS_REPORT.matcher(path.getFileName().toString()).matches())
                    .sorted(Comparator.comparing(path -> path.toAbsolutePath().toString()))
                    .toList()) {
                try (InputStream inputStream = Files.newInputStream(reportFile)) {
                    Map<String, List<String>> report = LibraryStatsSupport.OBJECT_MAPPER.readValue(inputStream, new TypeReference<>() {
                    });
                    for (Map.Entry<String, List<String>> entry : report.entrySet()) {
                        for (String rawFrame : entry.getValue()) {
                            DynamicAccessReportsParser.ParsedStackFrame frame = DynamicAccessReportsParser.parseStackFrame(rawFrame);
                            if (frame != null) {
                                candidates.add(new LibraryStatsSupport.AgentCoveredCallSite(
                                        entry.getKey(),
                                        frame.className(),
                                        frame.methodName()
                                ));
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new GradleException("Failed to load agent-origin candidates from " + dynamicAccessDir, e);
        }
        return candidates;
    }

    private static List<Path> listAgentOriginsFiles(Path originsOutput) {
        if (Files.isRegularFile(originsOutput)) {
            return List.of(originsOutput);
        }
        if (!Files.isDirectory(originsOutput)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(originsOutput)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith("-origins.txt"))
                    .sorted(Comparator.comparing(path -> path.toAbsolutePath().toString()))
                    .toList();
        } catch (IOException e) {
            throw new GradleException("Failed to list native-image-agent origins in " + originsOutput, e);
        }
    }

    private static void parseAgentOriginsFile(
            Path originsFile,
            Map<String, List<LibraryStatsSupport.AgentCoveredCallSite>> candidatesByTrackedApi,
            int candidateCount,
            Set<LibraryStatsSupport.AgentCoveredCallSite> coveredCallSites
    ) {
        List<AgentOriginMethod> currentPath = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(originsFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                ParsedAgentOriginLine parsedLine = parseAgentOriginLine(line);
                if (parsedLine == null) {
                    continue;
                }
                if (parsedLine.depth() > currentPath.size()) {
                    throw new GradleException("Invalid native-image-agent origin depth in " + originsFile);
                }
                while (currentPath.size() > parsedLine.depth()) {
                    currentPath.remove(currentPath.size() - 1);
                }
                currentPath.add(parsedLine.method());
                if (parsedLine.hasConfiguration()) {
                    matchAgentOriginPath(currentPath, candidatesByTrackedApi, coveredCallSites);
                    if (coveredCallSites.size() == candidateCount) {
                        return;
                    }
                }
            }
        } catch (IOException e) {
            throw new GradleException("Failed to parse native-image-agent origins " + originsFile, e);
        }
    }

    private static ParsedAgentOriginLine parseAgentOriginLine(String line) {
        int branchIndex = Math.max(line.lastIndexOf("├── "), line.lastIndexOf("└── "));
        if (branchIndex < 2) {
            return null;
        }
        String branchPrefix = line.substring(2, branchIndex);
        if (branchPrefix.length() % 4 != 0) {
            return null;
        }
        int depth = branchPrefix.length() / 4;
        String entry = line.substring(branchIndex + 4);
        int configurationIndex = entry.indexOf(" - [");
        boolean hasConfiguration = configurationIndex >= 0;
        String methodSignature = hasConfiguration ? entry.substring(0, configurationIndex) : entry;
        int hashIndex = methodSignature.indexOf('#');
        int parametersIndex = methodSignature.indexOf('(', hashIndex + 1);
        if (hashIndex < 1 || parametersIndex < 0) {
            return null;
        }
        AgentOriginMethod method = new AgentOriginMethod(
                normalizeMethodSignature(methodSignature),
                methodSignature.substring(0, hashIndex),
                methodSignature.substring(hashIndex + 1, parametersIndex)
        );
        return new ParsedAgentOriginLine(depth, method, hasConfiguration);
    }

    private static void matchAgentOriginPath(
            List<AgentOriginMethod> originPath,
            Map<String, List<LibraryStatsSupport.AgentCoveredCallSite>> candidatesByTrackedApi,
            Set<LibraryStatsSupport.AgentCoveredCallSite> coveredCallSites
    ) {
        for (int apiIndex = 0; apiIndex < originPath.size(); apiIndex++) {
            AgentOriginMethod apiMethod = originPath.get(apiIndex);
            List<LibraryStatsSupport.AgentCoveredCallSite> candidates = candidatesByTrackedApi.get(apiMethod.signature());
            if (candidates == null) {
                continue;
            }
            for (int callerIndex = apiIndex - 1; callerIndex >= 0; callerIndex--) {
                AgentOriginMethod callerMethod = originPath.get(callerIndex);
                if (candidatesByTrackedApi.containsKey(callerMethod.signature())) {
                    break;
                }
                List<LibraryStatsSupport.AgentCoveredCallSite> matches = candidates.stream()
                        .filter(candidate -> candidate.className().equals(callerMethod.className()))
                        .filter(candidate -> candidate.methodName().equals(callerMethod.methodName()))
                        .toList();
                if (!matches.isEmpty()) {
                    coveredCallSites.addAll(matches);
                    break;
                }
            }
        }
    }

    private static String normalizeMethodSignature(String methodSignature) {
        return methodSignature.replace(" ", "");
    }

    private record AgentOriginMethod(String signature, String className, String methodName) {
    }

    private record ParsedAgentOriginLine(int depth, AgentOriginMethod method, boolean hasConfiguration) {
    }
}
