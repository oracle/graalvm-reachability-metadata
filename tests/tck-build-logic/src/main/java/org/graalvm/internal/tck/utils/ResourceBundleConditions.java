/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.utils;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.graalvm.buildtools.gradle.dsl.GraalVMExtension;
import org.graalvm.buildtools.gradle.dsl.agent.AgentOptions;
import org.graalvm.buildtools.gradle.dsl.agent.ConditionalAgentModeOptions;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.testing.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/// Puts back the `typeReached` condition the conditional agent infers for a resource bundle but
/// prints without (§FS-metadata.2.3, §graal/GR-80923).
///
/// The agent keeps one bundle record per inferred condition, so a bundle requested under three
/// conditions comes out as three identical unconditional entries. The conditions are recovered by
/// merging every one of the agent's call trees a second time with each bundle request standing in
/// as a [#MARKER] glob, whose condition the agent prints correctly. Every tree takes part because
/// the merge attributes an entry by comparing a method's calls across all sessions. This runs in
/// two halves inside the agent's test task: [#keepTrees] before native-build-tools merges and
/// deletes the trees, and [#restore] after. Neither changes anything once the agent prints the
/// condition.
public final class ResourceBundleConditions {

    /// The glob prefix a bundle request stands in as; no resource on a classpath carries it.
    static final String MARKER = "GR-80923-bundle/";
    private static final String TREE = "partial-config-with-origins.json";
    private static final String METADATA = "reachability-metadata.json";
    private static final String GLOB_METACHARACTERS = "*?{}[]\\";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /// Merges agent sessions as the agent's own conditional merge does and returns the metadata.
    @FunctionalInterface
    interface ConditionalMerge {
        JsonNode merge(List<Path> sessionDirectories) throws IOException, InterruptedException;
    }

    /// Runs one half of the step in `test`; it may fail on I/O or be interrupted.
    @FunctionalInterface
    private interface Half {
        void run(Task test) throws IOException, InterruptedException;
    }

    private ResourceBundleConditions() {
    }

    /// Adds both halves to `project`'s `test` task when `-Pagent` runs the conditional agent with
    /// call trees. The first `doLast` is added now, ahead of the ones native-build-tools adds at
    /// `afterEvaluate` to merge and delete the trees; the second at a later `afterEvaluate`.
    public static void install(Project project, String nativeImageUtils) {
        Provider<String> agentMode = project.getProviders().gradleProperty("agent");
        if (!agentMode.isPresent()) {
            return;
        }
        DirectoryProperty buildDirectory = project.getLayout().getBuildDirectory();
        Provider<Directory> agentOutput = buildDirectory.dir(GeneralUtils.AGENT_OUTPUT_DIRECTORY);
        Provider<Directory> keptTrees = buildDirectory.dir(GeneralUtils.AGENT_OUTPUT_DIRECTORY + "-bundle-trees");
        Property<Boolean> writesCallTrees = project.getObjects().property(Boolean.class);
        addToTest(project, "GR-80923: keep the agent's call trees", test -> {
            if (writesCallTrees.get()) {
                keepTrees(path(agentOutput), path(keptTrees));
            }
        });
        project.afterEvaluate(evaluated -> {
            AgentOptions agent = project.getExtensions().getByType(GraalVMExtension.class).getAgent();
            ConditionalAgentModeOptions conditional = agent.getModes().getConditional();
            String mode = agentMode.get().isEmpty() ? agent.getDefaultMode().get() : agentMode.get();
            writesCallTrees.set(mode.equals("conditional") && conditional.getParallel().get());
            if (!writesCallTrees.get()) {
                return;
            }
            String extraFilter = conditional.getExtraFilterPath().getOrElse("");
            ConditionalMerge merge = nativeImageUtils(nativeImageUtils,
                    project.file(conditional.getUserCodeFilterPath().get()).toPath(),
                    extraFilter.isEmpty() ? null : project.file(extraFilter).toPath());
            addToTest(project, "GR-80923: restore resource bundle conditions", test -> {
                List<String> restored = restore(path(agentOutput), path(keptTrees), merge);
                if (!restored.isEmpty()) {
                    test.getLogger().lifecycle("GR-80923: restored the conditions of resource bundles {}", restored);
                }
            });
        });
    }

    /// Copies every session call tree under `agentOutputDirectory` into `keptDirectory`, with
    /// each bundle request replaced by its [#MARKER] glob. Fails when there is no tree to keep.
    static void keepTrees(Path agentOutputDirectory, Path keptDirectory) throws IOException {
        MetadataGenerationUtils.deleteRecursively(keptDirectory);
        List<Path> trees = sessionTrees(agentOutputDirectory);
        if (trees.isEmpty()) {
            throw new IllegalStateException("""
                    GR-80923: the conditional agent ran, but %s holds no session call tree: the step \
                    that keeps them for restoring resource bundle conditions ran after native-build-tools \
                    deleted them, and must run before its merge (FS-metadata.2.3).""".formatted(agentOutputDirectory));
        }
        for (Path tree : trees) {
            Path kept = keptDirectory.resolve(tree.getParent().getFileName()).resolve(TREE);
            Files.createDirectories(kept.getParent());
            disguiseBundles(tree, kept);
        }
    }

    /// Gives each unconditional bundle in the agent's merged output the conditions that merging
    /// the trees [#keepTrees] kept derives for its name, deletes those trees, and returns the names.
    static List<String> restore(Path agentOutputDirectory, Path keptDirectory, ConditionalMerge merge)
            throws IOException, InterruptedException {
        try {
            if (!sessionTrees(agentOutputDirectory).isEmpty()) {
                throw new IllegalStateException("GR-80923: bundle conditions are restored once "
                        + "native-build-tools has merged and deleted the agent sessions in " + agentOutputDirectory);
            }
            List<Path> sessions = sessionTrees(keptDirectory).stream().map(Path::getParent).toList();
            Path metadataFile = agentOutputDirectory.resolve(METADATA);
            if (!Files.isRegularFile(metadataFile)) {
                if (!sessions.isEmpty()) {
                    throw new IllegalStateException("GR-80923: call trees were kept in " + keptDirectory
                            + ", but native-build-tools wrote no merged agent output to " + metadataFile);
                }
                return List.of();
            }
            JsonNode metadata = OBJECT_MAPPER.readTree(metadataFile.toFile());
            List<String> bundles = unconditionalBundles(metadata);
            if (bundles.isEmpty()) {
                return bundles;
            }
            if (sessions.isEmpty()) {
                throw new IllegalStateException("GR-80923: no call tree was kept in " + keptDirectory
                        + " to restore the conditions of resource bundles " + bundles
                        + ": the step that keeps them did not run before native-build-tools' merge");
            }
            restore(metadata, markerConditions(merge.merge(sessions)));
            OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(metadataFile.toFile(), metadata);
            return bundles;
        } finally {
            MetadataGenerationUtils.deleteRecursively(keptDirectory);
        }
    }

    /// Fails when `metadataFile` still has a bundle without a condition, which means it did not
    /// come out of a test run that restored it.
    static void requireRestored(Path metadataFile) throws IOException {
        if (!Files.isRegularFile(metadataFile)) {
            return;
        }
        List<String> bundles = unconditionalBundles(OBJECT_MAPPER.readTree(metadataFile.toFile()));
        if (!bundles.isEmpty()) {
            throw new IllegalStateException("""
                    GR-80923: %s has resource bundles without a condition: %s.
                    The harness restores them in the test task only when the conditional agent writes \
                    call trees, so this output did not come through that step: check that the test \
                    project runs the conditional agent with `parallel` left on (FS-metadata.2.3).\
                    """.formatted(metadataFile, bundles));
        }
    }

    /// The names of the bundles in `metadata` that carry no condition, in order of appearance.
    static List<String> unconditionalBundles(JsonNode metadata) {
        Set<String> bundles = new LinkedHashSet<>();
        for (JsonNode entry : metadata.path("resources")) {
            if (isUnconditionalBundle(entry)) {
                bundles.add(entry.get("bundle").asText());
            }
        }
        return List.copyOf(bundles);
    }

    /// A [ConditionalMerge] that runs `generate-conditional` of `executable`, `native-image-utils`,
    /// with the filters the agent's own merge uses; `classNameFilter` may be `null`. A failure
    /// carries the tool's output.
    static ConditionalMerge nativeImageUtils(String executable, Path userCodeFilter, Path classNameFilter) {
        return sessionDirectories -> {
            Path outputDirectory = Files.createTempDirectory("gr-80923-bundles-");
            try {
                List<String> command = new ArrayList<>(List.of(
                        executable, "generate-conditional", "--user-code-filter=" + userCodeFilter));
                if (classNameFilter != null) {
                    command.add("--class-name-filter=" + classNameFilter);
                }
                sessionDirectories.forEach(session -> command.add("--input-dir=" + session));
                command.add("--output-dir=" + outputDirectory);
                Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
                String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                int exitCode = process.waitFor();
                if (exitCode != 0) {
                    throw new IOException("GR-80923: %s exited with %d:%n%s"
                            .formatted(String.join(" ", command), exitCode, output));
                }
                return OBJECT_MAPPER.readTree(outputDirectory.resolve(METADATA).toFile());
            } finally {
                MetadataGenerationUtils.deleteRecursively(outputDirectory);
            }
        };
    }

    /// Replaces each unconditional bundle in `metadata` with one entry per condition `conditions`
    /// holds for its name, keeping its other fields, and drops the copies this makes identical.
    /// Fails, naming every such bundle, when a name has no condition or a request outside user code.
    static void restore(JsonNode metadata, Map<String, Set<JsonNode>> conditions) {
        if (!(metadata.get("resources") instanceof ArrayNode resources)) {
            return;
        }
        Map<String, String> unresolved = new TreeMap<>();
        Set<JsonNode> restored = new LinkedHashSet<>();
        for (JsonNode entry : resources) {
            if (!isUnconditionalBundle(entry)) {
                restored.add(entry);
                continue;
            }
            String name = entry.get("bundle").asText();
            Set<JsonNode> derived = conditions.getOrDefault(name, Set.of());
            if (derived.isEmpty()) {
                unresolved.put(name, "no call tree kept from this run requests it");
            } else if (derived.stream().anyMatch(JsonNode::isMissingNode)) {
                unresolved.put(name, "it is requested where no user code is on the stack");
            }
            for (JsonNode condition : derived) {
                ObjectNode conditional = OBJECT_MAPPER.createObjectNode();
                conditional.set("condition", condition.deepCopy());
                conditional.setAll((ObjectNode) entry);
                restored.add(conditional);
            }
        }
        if (!unresolved.isEmpty()) {
            String reasons = unresolved.entrySet().stream()
                    .map(bundle -> "  - " + bundle.getKey() + ": " + bundle.getValue())
                    .collect(Collectors.joining("\n"));
            throw new IllegalStateException("""
                    GR-80923: the Native Image agent printed these resource bundles without the \
                    typeReached condition it inferred, and none can be derived for them:
                    %s
                    Generation stops rather than write them unconditional (FS-metadata.2.3).""".formatted(reasons));
        }
        resources.removeAll();
        resources.addAll(restored);
    }

    /// The conditions `markers` gives each bundle name, a missing node standing for a request
    /// the agent could not attribute to user code.
    static Map<String, Set<JsonNode>> markerConditions(JsonNode markers) {
        Map<String, Set<JsonNode>> conditions = new TreeMap<>();
        for (JsonNode entry : markers.path("resources")) {
            String glob = entry.path("glob").asText();
            if (glob.startsWith(MARKER)) {
                conditions.computeIfAbsent(glob.substring(MARKER.length()),
                        name -> new TreeSet<>(Comparator.comparing(JsonNode::toString))).add(entry.path("condition"));
            }
        }
        return conditions;
    }

    /// Streams `tree` into `disguised`, replacing every bundle request in a node's `config` by its
    /// [#MARKER] glob and copying everything else as it is.
    static void disguiseBundles(Path tree, Path disguised) throws IOException {
        try (JsonParser parser = OBJECT_MAPPER.createParser(tree.toFile());
             JsonGenerator generator = OBJECT_MAPPER.createGenerator(disguised.toFile(), JsonEncoding.UTF8)) {
            while (parser.nextToken() != null) {
                if (parser.currentToken() == JsonToken.FIELD_NAME && parser.currentName().equals("config")) {
                    generator.writeFieldName("config");
                    parser.nextToken();
                    JsonNode config = parser.readValueAsTree();
                    disguiseBundles(config);
                    generator.writeTree(config);
                } else {
                    generator.copyCurrentEvent(parser);
                }
            }
        }
    }

    private static void disguiseBundles(JsonNode config) {
        for (JsonNode resources : config.findValues("resources")) {
            if (!(resources instanceof ArrayNode entries)) {
                continue;
            }
            for (int i = 0; i < entries.size(); i++) {
                if (entries.get(i).get("bundle") instanceof JsonNode bundle) {
                    entries.set(i, OBJECT_MAPPER.createObjectNode().put("glob", marker(bundle.asText())));
                }
            }
        }
    }

    private static String marker(String bundle) {
        if (bundle.chars().anyMatch(character -> GLOB_METACHARACTERS.indexOf(character) >= 0)) {
            throw new IllegalStateException(
                    "GR-80923: resource bundle " + bundle + " cannot stand in as a glob to restore its condition");
        }
        return MARKER + bundle;
    }

    private static boolean isUnconditionalBundle(JsonNode entry) {
        return entry.has("bundle") && !entry.has("condition");
    }

    /// The call tree of every session directly under `directory`, sorted; none when it is absent.
    private static List<Path> sessionTrees(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> sessions = Files.list(directory)) {
            return sessions.map(session -> session.resolve(TREE)).filter(Files::isRegularFile).sorted().toList();
        }
    }

    private static void addToTest(Project project, String name, Half half) {
        project.getTasks().withType(Test.class).configureEach(test -> {
            if (test.getName().equals("test")) {
                test.doLast(name, task -> {
                    try {
                        half.run(task);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(e);
                    }
                });
            }
        });
    }

    private static Path path(Provider<Directory> directory) {
        return directory.get().getAsFile().toPath();
    }
}
