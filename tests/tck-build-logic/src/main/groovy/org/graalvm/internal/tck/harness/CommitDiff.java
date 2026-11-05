/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness;

import groovy.json.JsonSlurper;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/// What a commit range changed, read from git: the libraries whose metadata or tests it touches,
/// the artifact-level `index.json` files it edits and the tested versions they add, and the
/// real-use projects it changes. The layout lookups stay on the [TckExtension] it reads them from.
final class CommitDiff {
    private final TckExtension tck;

    CommitDiff(TckExtension tck) {
        this.tck = tck;
    }

    /**
     * Returns a list of coordinates that match changed files between baseCommit and newCommit.
     *
     * @return List of coordinates
     */
    List<String> diffCoordinates(String baseCommit, String newCommit) {
        Path workflowsRoot = tck.repoRoot().resolve(".github").resolve("workflows");
        Map<String, List<Path>> changed = diffFiles(baseCommit, newCommit, "ACMRTD").stream()
                .collect(Collectors.groupingBy((Path path) -> {
                    if (path.startsWith(tck.tckRoot()) || path.startsWith(workflowsRoot)) {
                        return "logic";
                    } else if (path.startsWith(tck.testRoot())) {
                        return "test";
                    } else if (path.startsWith(tck.metadataRoot())) {
                        return "metadata";
                    } else {
                        return "other";
                    }
                }));

        List<Path> changedMetadataFiles = changed.getOrDefault("metadata", Collections.emptyList());
        List<Path> changedTestFiles = changed.getOrDefault("test", Collections.emptyList());
        List<Path> changedLogicFiles = changed.getOrDefault("logic", Collections.emptyList());

        // if we didn't change any of metadata, tests or logic we don't need to test anything
        if (changedMetadataFiles.isEmpty() && changedTestFiles.isEmpty() && changedLogicFiles.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> changedCoordinates = tck.getMatchingCoordinatesStrict("").stream().filter(c -> {
            Path metadataDir = tck.getMetadataDir(c);
            if (changedMetadataFiles.stream().anyMatch(f -> f.startsWith(metadataDir))) {
                return true;
            }
            Path testDir = tck.getTestDir(c);
            return changedTestFiles.stream().anyMatch(f -> f.startsWith(testDir));
        }).distinct().collect(Collectors.toCollection(ArrayList::new));

        return changedCoordinates;
    }

    /**
     * Returns a list of changed artifact coordinates based on artifact-level index.json files modified between baseCommit and newCommit.
     *
     * @return List of coordinates (e.g., "org.flywaydb:flyway-core" or "org.example:library")
     */
    public List<String> diffIndexCoordinates(String baseCommit, String newCommit) {
        return diffFiles(baseCommit, newCommit, "ACMRT").stream()
                .map(path -> tck.repoRoot().relativize(path).toString().replace('\\', '/'))
                .map(this::indexPathToCoordinate)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * Returns changed artifact coordinates paired with only newly added tested-versions from artifact-level index.json diffs.
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> diffIndexTestedVersions(String baseCommit, String newCommit) {
        return diffFiles(baseCommit, newCommit, "ACMRT").stream()
                .map(path -> tck.repoRoot().relativize(path).toString().replace('\\', '/'))
                .map(relativePath -> diffIndexTestedVersionsEntry(baseCommit, newCommit, relativePath))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Returns the sorted coordinates of the real-use projects newCommit changes since it forked from
     * baseCommit that still exist at newCommit's checkout (§real-use-tests/AR-real-use-test-harness.4).
     */
    public List<String> diffRealUseCoordinates(String baseCommit, String newCommit) {
        Path realUseRoot = tck.repoRoot().resolve("real-use-tests").resolve("src");
        // From the merge base: a project baseCommit gained after the fork is not newCommit's change.
        return diffRevisions(List.of(baseCommit + "..." + newCommit), "ACMRTD").stream()
                .filter(path -> path.startsWith(realUseRoot) && realUseRoot.relativize(path).getNameCount() > 3)
                .map(path -> realUseRoot.relativize(path))
                .map(relative -> relative.getName(0) + ":" + relative.getName(1) + ":" + relative.getName(2))
                .distinct()
                .filter(coordinates -> tck.getRealUseTestDir(coordinates).isPresent())
                .sorted()
                .collect(Collectors.toList());
    }

    private List<Path> diffFiles(String baseCommit, String newCommit, String diffFilter) {
        return diffRevisions(List.of(baseCommit, newCommit), diffFilter);
    }

    private List<Path> diffRevisions(List<String> revisions, String diffFilter) {
        List<String> command = new ArrayList<>(List.of("git", "diff", "--name-only", "--diff-filter=" + diffFilter));
        command.addAll(revisions);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        tck.getExecOperations().exec(spec -> {
            spec.setStandardOutput(baos);
            spec.commandLine(command);
        });

        return Arrays.stream(baos.toString(StandardCharsets.UTF_8).split("\\r?\\n"))
                .filter(line -> !line.isBlank())
                .map(line -> tck.repoRoot().resolve(line))
                .collect(Collectors.toList());
    }

    /**
     * Maps an artifact-level metadata index.json file path to its corresponding coordinate string.
     *
     * @return the coordinate string (G:A), or null if not a match
     */
    private String indexPathToCoordinate(String path) {
        Pattern pattern = Pattern.compile("(?:metadata|tests/src)/([^/]+)/([^/]+)(?:/([^/]+))?/index\\.json");
        Matcher matcher = pattern.matcher(path);

        if (matcher.matches()) {
            String group = matcher.group(1);
            String artifact = matcher.group(2);
            String version = matcher.group(3);

            return (version != null)
                    ? String.format("%s:%s:%s", group, artifact, version)
                    : String.format("%s:%s", group, artifact);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> diffIndexTestedVersionsEntry(String baseCommit, String newCommit, String relativePath) {
        String coordinates = indexPathToCoordinate(relativePath);
        if (coordinates == null) {
            return null;
        }

        List<Map<String, ?>> baseEntries = readJsonListFromCommit(baseCommit, relativePath);
        List<Map<String, ?>> headEntries = readJsonListFromCommit(newCommit, relativePath);

        Map<String, Set<String>> baseByMetadataVersion = testedVersionsByMetadataVersion(baseEntries);
        Map<String, Set<String>> headByMetadataVersion = testedVersionsByMetadataVersion(headEntries);

        List<Map<String, Object>> changedMetadataVersions = new ArrayList<>();
        for (Map.Entry<String, Set<String>> headEntry : headByMetadataVersion.entrySet()) {
            String metadataVersion = headEntry.getKey();
            Set<String> baseVersions = baseByMetadataVersion.getOrDefault(metadataVersion, Collections.emptySet());
            List<String> addedVersions = headEntry.getValue().stream()
                    .filter(testedVersion -> !baseVersions.contains(testedVersion))
                    .toList();
            if (!addedVersions.isEmpty()) {
                changedMetadataVersions.add(Map.of(
                        "coordinates", coordinates + ":" + metadataVersion,
                        "versions", addedVersions
                ));
            }
        }

        if (changedMetadataVersions.isEmpty()) {
            return null;
        }

        if (changedMetadataVersions.size() == 1) {
            return changedMetadataVersions.get(0);
        }

        return Map.of(
                "entries", changedMetadataVersions
        );
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, ?>> readJsonListFromCommit(String commit, String relativePath) {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        var result = tck.getExecOperations().exec(spec -> {
            spec.setIgnoreExitValue(true);
            spec.setStandardOutput(stdout);
            spec.setErrorOutput(stderr);
            spec.commandLine("git", "show", commit + ":" + relativePath);
        });
        if (result.getExitValue() != 0) {
            return Collections.emptyList();
        }
        String json = stdout.toString(StandardCharsets.UTF_8);
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        Object parsed = parseTextJson(json);
        if (parsed instanceof List<?> list) {
            return list.stream()
                    .filter(Map.class::isInstance)
                    .map(entry -> (Map<String, ?>) entry)
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Set<String>> testedVersionsByMetadataVersion(List<Map<String, ?>> entries) {
        Map<String, Set<String>> testedVersions = new LinkedHashMap<>();
        for (Map<String, ?> entry : entries) {
            if (entry == null) {
                continue;
            }
            Object metadataVersion = entry.get("metadata-version");
            Object testedVersionsRaw = entry.get("tested-versions");
            if (!(metadataVersion instanceof String metadataVersionString) || !(testedVersionsRaw instanceof List<?> versions)) {
                continue;
            }
            testedVersions.put(
                    metadataVersionString,
                    versions.stream()
                            .map(Object::toString)
                            .collect(Collectors.toCollection(LinkedHashSet::new))
            );
        }
        return testedVersions;
    }

    private Object parseTextJson(String json) {
        try {
            return new JsonSlurper().parseText(json);
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }
}
