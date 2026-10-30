/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/// Restoring the resource bundle conditions the agent drops (GR-80923), for each bundle shape
/// the agent writes.
class ResourceBundleConditionsTests {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /// A call tree as the conditional agent writes it: `I18n` requests a properties bundle and
    /// probes its file, and `ClassBundleUser` requests a class-based JDK bundle.
    private static final String TREE = """
            {
              "configuration-with-origins": {
                "class": "<root>", "method": "<root>", "signature": "()V",
                "methods": [
                  {"class": "org.example.I18n", "method": "<clinit>", "signature": "()V", "methods": [
                    {"class": "java.util.ResourceBundle", "method": "getBundleImpl", "signature": "()V",
                     "methods": [],
                     "config":{"reachability-metadata":{"resources":[{"bundle":"org/example/messages"},\
            {"glob":"org/example/messages.properties"}]}}}
                  ]},
                  {"class": "org.example.ClassBundleUser", "method": "log", "signature": "()V", "methods": [],
                   "config":{"reachability-metadata":{"resources":[{"bundle":"sun.util.logging.resources.logging"}],\
            "reflection":[{"type":"org.example.Kept"}]}}}
                ]
              }
            }
            """;

    /// A call tree with no bundle request.
    private static final String TREE_WITHOUT_BUNDLES = """
            {"configuration-with-origins": {"class": "<root>", "method": "<root>", "signature": "()V",
              "methods": [], "config":{"reachability-metadata":{"resources":[{"glob":"org/example/a.txt"}]}}}}
            """;

    @TempDir
    Path tempDir;

    private Path agentOutput;
    private Path keptTrees;

    @BeforeEach
    void layOutAgentOutput() throws IOException {
        agentOutput = tempDir.resolve("agent-output/test");
        keptTrees = tempDir.resolve("agent-output/test-bundle-trees");
        writeSession("session-1", TREE);
        writeSession("session-2", TREE_WITHOUT_BUNDLES);
    }

    /// Every session is kept, also one without a bundle request: the merge compares a method's
    /// calls across all sessions.
    @Test
    void keepTreesKeepsEverySessionWithEachBundleRequestDisguised() throws IOException {
        ResourceBundleConditions.keepTrees(agentOutput, keptTrees);

        JsonNode kept = OBJECT_MAPPER.readTree(keptTrees.resolve("session-1/partial-config-with-origins.json").toFile());
        JsonNode expected = OBJECT_MAPPER.readTree(TREE
                .replace("{\"bundle\":\"org/example/messages\"}", "{\"glob\":\"GR-80923-bundle/org/example/messages\"}")
                .replace("{\"bundle\":\"sun.util.logging.resources.logging\"}",
                        "{\"glob\":\"GR-80923-bundle/sun.util.logging.resources.logging\"}"));
        assertThat(kept).isEqualTo(expected);
        assertThat(OBJECT_MAPPER.readTree(keptTrees.resolve("session-2/partial-config-with-origins.json").toFile()))
                .isEqualTo(OBJECT_MAPPER.readTree(TREE_WITHOUT_BUNDLES));
    }

    @Test
    void keepTreesFailsNamingTheWiringWhenNoSessionTreeIsLeft() throws IOException {
        MetadataGenerationUtils.deleteRecursively(agentOutput);

        assertThatThrownBy(() -> ResourceBundleConditions.keepTrees(agentOutput, keptTrees))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GR-80923")
                .hasMessageContaining("ran after native-build-tools deleted them");
    }

    /// Two JVMs, as under `forkEvery`: `Api.call` reaches the bundle through `Util.helper`, which
    /// `Other.b` also calls without it. Across both sessions the agent's real merge attributes the
    /// bundle to `Api`; the first session alone would attribute it to `Util`.
    @Test
    void restoreDerivesTheAgentsConditionAcrossSessionsWithTheRealMerge() throws Exception {
        Path tool = Stream.of(System.getenv("GRAALVM_HOME"), System.getenv("JAVA_HOME"))
                .filter(Objects::nonNull)
                .flatMap(home -> Stream.of(Path.of(home, "bin/native-image-utils"),
                        Path.of(home, "lib/svm/bin/native-image-utils")))
                .filter(Files::isExecutable)
                .findFirst().orElse(null);
        assumeTrue(tool != null, "needs native-image-utils from a GraalVM in GRAALVM_HOME or JAVA_HOME");
        MetadataGenerationUtils.deleteRecursively(agentOutput);
        writeSession("session-1", helperTree("Api", "call", """
                "java.util.ResourceBundle","method":"getBundleImpl","signature":"()V","methods":[],
                "config":{"reachability-metadata":{"resources":[{"bundle":"org.example.messages"}]}}"""));
        writeSession("session-2", helperTree("Other", "b", """
                "java.util.Properties","method":"load","signature":"()V","methods":[],
                "config":{"reachability-metadata":{"resources":[{"glob":"org/example/other.txt"}]}}"""));
        Path filter = tempDir.resolve("user-code-filter.json");
        Files.writeString(filter, """
                {"rules": [{"includeClasses": "org.example.**"}]}
                """, StandardCharsets.UTF_8);
        ResourceBundleConditions.ConditionalMerge merge = ResourceBundleConditions.nativeImageUtils(
                tool.toString(), filter, null);
        ResourceBundleConditions.keepTrees(agentOutput, keptTrees);

        Map<String, Set<JsonNode>> firstSessionOnly = ResourceBundleConditions.markerConditions(
                merge.merge(List.of(keptTrees.resolve("session-1"))));
        assertThat(firstSessionOnly.get("org.example.messages"))
                .containsExactly(OBJECT_MAPPER.readTree("""
                        {"typeReached": "org.example.Util"}
                        """));

        MetadataGenerationUtils.deleteRecursively(agentOutput);
        Files.createDirectories(agentOutput);
        writeMergedOutput("""
                {"resources": [
                  {"condition": {"typeReached": "org.example.Other"}, "glob": "org/example/other.txt"},
                  {"bundle": "org.example.messages"}
                ]}
                """);
        ResourceBundleConditions.restore(agentOutput, keptTrees, merge);

        assertThat(readMergedOutput()).isEqualTo(OBJECT_MAPPER.readTree("""
                {"resources": [
                  {"condition": {"typeReached": "org.example.Other"}, "glob": "org/example/other.txt"},
                  {"condition": {"typeReached": "org.example.Api"}, "bundle": "org.example.messages"}
                ]}
                """));
    }

    @Test
    void restoreGivesAPropertiesBundleTheConditionOfItsRequest() throws Exception {
        writeMergedOutput("""
                {"resources": [
                  {"bundle": "org/example/messages"},
                  {"condition": {"typeReached": "org.example.I18n"}, "glob": "org/example/messages.properties"}
                ]}
                """);
        List<Path> merged = new ArrayList<>();

        restore(sessions -> {
            merged.addAll(sessions);
            assertThat(sessions.get(0).resolve("partial-config-with-origins.json"))
                    .content().contains("GR-80923-bundle/org/example/messages");
            return markers("""
                    {"condition": {"typeReached": "org.example.I18n"}, "glob": "GR-80923-bundle/org/example/messages"}
                    """);
        });

        assertThat(merged).containsExactly(keptTrees.resolve("session-1"), keptTrees.resolve("session-2"));
        assertThat(readMergedOutput()).isEqualTo(OBJECT_MAPPER.readTree("""
                {"resources": [
                  {"condition": {"typeReached": "org.example.I18n"}, "bundle": "org/example/messages"},
                  {"condition": {"typeReached": "org.example.I18n"}, "glob": "org/example/messages.properties"}
                ]}
                """));
        assertThat(keptTrees).doesNotExist();
    }

    /// The agent repeats the bundle once per condition, while its `.properties` glob carries only
    /// the first: the conditions come from the requests, not from the globs.
    @Test
    void restoreGivesABundleOneEntryPerConditionItIsRequestedUnder() throws Exception {
        writeMergedOutput("""
                {"resources": [
                  {"bundle": "org/example/messages"},
                  {"bundle": "org/example/messages"},
                  {"bundle": "org/example/messages"},
                  {"condition": {"typeReached": "org.example.A"}, "glob": "org/example/messages.properties"}
                ]}
                """);

        List<String> restored = restore(sessions -> markers("""
                {"condition": {"typeReached": "org.example.C"}, "glob": "GR-80923-bundle/org/example/messages"},
                {"condition": {"typeReached": "org.example.A"}, "glob": "GR-80923-bundle/org/example/messages"},
                {"condition": {"typeReached": "org.example.B"}, "glob": "GR-80923-bundle/org/example/messages"}
                """));

        assertThat(readMergedOutput()).isEqualTo(OBJECT_MAPPER.readTree("""
                {"resources": [
                  {"condition": {"typeReached": "org.example.A"}, "bundle": "org/example/messages"},
                  {"condition": {"typeReached": "org.example.B"}, "bundle": "org/example/messages"},
                  {"condition": {"typeReached": "org.example.C"}, "bundle": "org/example/messages"},
                  {"condition": {"typeReached": "org.example.A"}, "glob": "org/example/messages.properties"}
                ]}
                """));
        assertThat(restored).containsExactly("org/example/messages");
    }

    /// A class-based JDK bundle has no `.properties` file of its own, only locale probes; it
    /// keeps its other fields.
    @Test
    void restoreKeepsTheFieldsOfAClassBasedBundle() throws Exception {
        writeMergedOutput("""
                {"resources": [
                  {"bundle": "sun.util.logging.resources.logging", "module": "java.logging"},
                  {"condition": {"typeReached": "org.example.ClassBundleUser"},
                   "glob": "sun/util/logging/resources/logging_en.properties"}
                ]}
                """);

        restore(sessions -> markers("""
                {"condition": {"typeReached": "org.example.ClassBundleUser"},
                 "glob": "GR-80923-bundle/sun.util.logging.resources.logging"}
                """));

        JsonNode bundle = readMergedOutput().get("resources").get(0);
        assertThat(bundle).isEqualTo(OBJECT_MAPPER.readTree("""
                {"condition": {"typeReached": "org.example.ClassBundleUser"},
                 "bundle": "sun.util.logging.resources.logging", "module": "java.logging"}
                """));
        assertThat(bundle.fieldNames().next()).isEqualTo("condition");
    }

    @Test
    void restoreFailsNamingEveryBundleNoConditionCanBeDerivedFor() throws Exception {
        String agentOutputJson = """
                {"resources": [
                  {"bundle": "org/example/fromRoot"},
                  {"bundle": "org/example/neverRequested"},
                  {"bundle": "org/example/messages"}
                ]}
                """;
        writeMergedOutput(agentOutputJson);

        assertThatThrownBy(() -> restore(sessions -> markers("""
                {"glob": "GR-80923-bundle/org/example/fromRoot"},
                {"condition": {"typeReached": "org.example.I18n"}, "glob": "GR-80923-bundle/org/example/messages"}
                """)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GR-80923")
                .hasMessageContaining("org/example/fromRoot: it is requested where no user code is on the stack")
                .hasMessageContaining("org/example/neverRequested: no call tree kept from this run requests it")
                .hasMessageNotContaining("org/example/messages:");
        assertThat(agentOutput.resolve("reachability-metadata.json")).content().isEqualTo(agentOutputJson);
        assertThat(keptTrees).doesNotExist();
    }

    @Test
    void restoreFailsWhenNoCallTreeWasKept() throws IOException {
        MetadataGenerationUtils.deleteRecursively(agentOutput.resolve("session-1"));
        MetadataGenerationUtils.deleteRecursively(agentOutput.resolve("session-2"));
        writeMergedOutput("""
                {"resources": [{"bundle": "org/example/messages"}]}
                """);

        assertThatThrownBy(() -> ResourceBundleConditions.restore(agentOutput, keptTrees, sessions -> {
            throw new AssertionError("nothing to merge");
        }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GR-80923: no call tree was kept")
                .hasMessageContaining("[org/example/messages]");
    }

    @Test
    void restoreFailsWhenTreesWereKeptButNothingWasMerged() throws IOException {
        ResourceBundleConditions.keepTrees(agentOutput, keptTrees);
        MetadataGenerationUtils.deleteRecursively(agentOutput);

        assertThatThrownBy(() -> ResourceBundleConditions.restore(agentOutput, keptTrees, sessions -> {
            throw new AssertionError("nothing to merge");
        }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("native-build-tools wrote no merged agent output");
    }

    /// Once the agent prints the condition itself, nothing is merged again and nothing changes.
    @Test
    void restoreLeavesAConditionalAgentOutputUntouched() throws Exception {
        String agentOutputJson = """
                {"resources": [
                  {"condition": {"typeReached": "org.example.I18n"}, "bundle": "org/example/messages"}
                ]}
                """;
        writeMergedOutput(agentOutputJson);

        List<String> restored = restore(sessions -> {
            throw new AssertionError("a conditional agent output is not merged again");
        });

        assertThat(restored).isEmpty();

        assertThat(agentOutput.resolve("reachability-metadata.json")).content().isEqualTo(agentOutputJson);
        assertThat(keptTrees).doesNotExist();
    }

    @Test
    void restoreRefusesToRunBeforeTheSessionsAreMerged() {
        assertThatThrownBy(() -> ResourceBundleConditions.restore(agentOutput, keptTrees, sessions -> {
            throw new AssertionError("nothing to merge");
        }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GR-80923");
    }

    @Test
    void requireRestoredNamesTheBundlesLeftWithoutACondition() throws IOException {
        Path metadata = tempDir.resolve("reachability-metadata.json");
        Files.writeString(metadata, """
                {"resources": [
                  {"bundle": "org/example/messages"},
                  {"bundle": "org/example/messages"},
                  {"condition": {"typeReached": "org.example.I18n"}, "bundle": "org/example/other"}
                ]}
                """, StandardCharsets.UTF_8);

        assertThatThrownBy(() -> ResourceBundleConditions.requireRestored(metadata))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GR-80923")
                .hasMessageContaining("[org/example/messages]");

        Files.writeString(metadata, """
                {"resources": [{"condition": {"typeReached": "org.example.I18n"}, "bundle": "org/example/other"}]}
                """, StandardCharsets.UTF_8);
        ResourceBundleConditions.requireRestored(metadata);
    }

    /// Keeps the trees, lets native-build-tools merge and delete the sessions, then restores.
    private List<String> restore(ResourceBundleConditions.ConditionalMerge merge) throws Exception {
        ResourceBundleConditions.keepTrees(agentOutput, keptTrees);
        MetadataGenerationUtils.deleteRecursively(agentOutput.resolve("session-1"));
        MetadataGenerationUtils.deleteRecursively(agentOutput.resolve("session-2"));
        return ResourceBundleConditions.restore(agentOutput, keptTrees, merge);
    }

    /// A session whose `org.example.<caller>.<method>` calls `Util.helper`, which calls `callee`.
    private static String helperTree(String caller, String method, String callee) {
        return """
                {"configuration-with-origins": {"class": "<root>", "method": "<root>", "signature": "()V", "methods": [
                  {"class": "org.example.%s", "method": "%s", "signature": "()V", "methods": [
                    {"class": "org.example.Util", "method": "helper", "signature": "(Z)V", "methods": [
                      {"class": %s}]}]}]}}
                """.formatted(caller, method, callee);
    }

    private void writeSession(String session, String tree) throws IOException {
        Path file = agentOutput.resolve(session).resolve("partial-config-with-origins.json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, tree, StandardCharsets.UTF_8);
    }

    private void writeMergedOutput(String json) throws IOException {
        Files.writeString(agentOutput.resolve("reachability-metadata.json"), json, StandardCharsets.UTF_8);
    }

    private JsonNode readMergedOutput() throws IOException {
        return OBJECT_MAPPER.readTree(agentOutput.resolve("reachability-metadata.json").toFile());
    }

    private static JsonNode markers(String entries) throws IOException {
        return OBJECT_MAPPER.readTree("{\"resources\": [" + entries + "]}");
    }
}
