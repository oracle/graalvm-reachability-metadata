/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss.jboss_common_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.jboss.util.Base64;
import org.jboss.util.Heap;
import org.jboss.util.JBossStringBuilder;
import org.jboss.util.graph.Edge;
import org.jboss.util.graph.Graph;
import org.jboss.util.graph.Vertex;
import org.jboss.util.graph.Visitor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class Jboss_common_coreTest {
    @TempDir
    private Path temporaryDirectory;

    @Test
    void jbossStringBuilderSupportsMutableCharSequenceOperations() {
        JBossStringBuilder builder = new JBossStringBuilder("common");

        builder.insert(0, "jboss-")
                .append('-')
                .append(2)
                .append('.')
                .append(0);
        assertThat(builder.toString()).isEqualTo("jboss-common-2.0");
        assertThat(builder.length()).isEqualTo(16);
        assertThat(builder.charAt(6)).isEqualTo('c');
        assertThat(builder.indexOf("common")).isEqualTo(6);
        assertThat(builder.lastIndexOf("-")).isEqualTo(12);
        assertThat(builder.substring(6, 12)).isEqualTo("common");
        assertThat(builder.subSequence(0, 5).toString()).isEqualTo("jboss");

        char[] copied = new char[6];
        builder.getChars(6, 12, copied, 0);
        assertThat(copied).containsExactly('c', 'o', 'm', 'm', 'o', 'n');

        builder.replace(6, 12, "core");
        assertThat(builder.toString()).isEqualTo("jboss-core-2.0");
        builder.delete(10, 14).deleteCharAt(0).setCharAt(0, 'B');
        assertThat(builder.toString()).isEqualTo("Boss-core");
        builder.reverse();
        assertThat(builder.toString()).isEqualTo("eroc-ssoB");
    }

    @Test
    void heapOrdersNaturalAndComparatorBackedValues() {
        Heap naturalHeap = new Heap();
        naturalHeap.insert(5);
        naturalHeap.insert(1);
        naturalHeap.insert(3);
        assertThat(naturalHeap.peek()).isEqualTo(1);
        assertThat(naturalHeap.extract()).isEqualTo(1);
        assertThat(naturalHeap.extract()).isEqualTo(3);
        assertThat(naturalHeap.extract()).isEqualTo(5);
        assertThat(naturalHeap.extract()).isNull();

        Heap lengthHeap = new Heap(
                (left, right) -> Integer.compare(((String) left).length(), ((String) right).length()));
        lengthHeap.insert("longest");
        lengthHeap.insert("mid");
        lengthHeap.insert("s");
        assertThat(lengthHeap.extract()).isEqualTo("s");
        assertThat(lengthHeap.extract()).isEqualTo("mid");
        lengthHeap.clear();
        assertThat(lengthHeap.peek()).isNull();
    }

    @Test
    void graphSupportsWeightedEdgesTraversalAndCycleDetection() {
        Graph<String> graph = new Graph<>();
        Vertex<String> compile = new Vertex<>("compile", "compile-sources");
        Vertex<String> test = new Vertex<>("test", "run-tests");
        Vertex<String> packageArchive = new Vertex<>("package", "create-archive");

        assertThat(graph.addVertex(compile)).isTrue();
        assertThat(graph.addVertex(test)).isTrue();
        assertThat(graph.addVertex(packageArchive)).isTrue();
        assertThat(graph.addEdge(compile, test, 2)).isTrue();
        assertThat(graph.addEdge(test, packageArchive, 3)).isTrue();

        List<String> depthFirstNames = new ArrayList<>();
        graph.depthFirstSearch(compile, (Visitor<String>) (visitedGraph, vertex) -> depthFirstNames.add(vertex.getName()));
        assertThat(depthFirstNames).containsExactly("compile", "test", "package");

        graph.clearMark();
        List<String> breadthFirstNames = new ArrayList<>();
        graph.breadthFirstSearch(compile, (Visitor<String>) (visitedGraph, vertex) -> breadthFirstNames.add(vertex.getName()));
        assertThat(breadthFirstNames).containsExactly("compile", "test", "package");

        assertThat(compile.cost(test)).isEqualTo(2);
        assertThat(graph.findVertexByName("package")).isSameAs(packageArchive);
        assertThat(graph.findCycles()).isEmpty();

        assertThat(graph.addEdge(packageArchive, compile, 5)).isTrue();
        Edge<String>[] cycles = graph.findCycles();
        assertThat(cycles).hasSize(1);
        assertThat(cycles[0].getFrom()).isSameAs(packageArchive);
        assertThat(cycles[0].getTo()).isSameAs(compile);
        assertThat(cycles[0].getCost()).isEqualTo(5);
    }

    @Test
    void base64EncodesAndDecodesByteRanges() {
        String message = "JBoss common core utilities";
        byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);
        byte[] prefixBytes = "prefix:".getBytes(StandardCharsets.UTF_8);
        String source = "prefix:" + message + ":suffix";
        byte[] sourceBytes = source.getBytes(StandardCharsets.UTF_8);

        String encoded = Base64.encodeBytes(
                sourceBytes,
                prefixBytes.length,
                messageBytes.length,
                Base64.DONT_BREAK_LINES);

        assertThat(encoded).doesNotContain("\n", "\r");
        assertThat(encoded).isEqualTo(java.util.Base64.getEncoder().encodeToString(messageBytes));
        assertThat(Base64.decode(encoded)).isEqualTo(messageBytes);
    }

    @Test
    void fileUtilitiesCopyEncodeDecodeAndDeleteTrees() throws Exception {
        Path source = temporaryDirectory.resolve("source.txt");
        Path copied = temporaryDirectory.resolve("nested").resolve("copied.txt");
        java.nio.file.Files.write(source, "JBoss file utility copy".getBytes(StandardCharsets.UTF_8));

        org.jboss.util.file.Files.copy(source.toUri().toURL(), copied.toFile());
        assertThat(java.nio.file.Files.readString(copied)).isEqualTo("JBoss file utility copy");

        Path copiedAgain = temporaryDirectory.resolve("copied-again.txt");
        org.jboss.util.file.Files.copy(copied.toFile(), copiedAgain.toFile(), 4);
        assertThat(java.nio.file.Files.readAllBytes(copiedAgain))
                .isEqualTo(java.nio.file.Files.readAllBytes(source));

        String originalName = "module:org.jboss/common core";
        String encodedName = org.jboss.util.file.Files.encodeFileName(originalName, '#');
        assertThat(encodedName).doesNotContain(":", "/", " ");
        assertThat(org.jboss.util.file.Files.decodeFileName(encodedName, '#')).isEqualTo(originalName);

        Path tree = temporaryDirectory.resolve("tree");
        java.nio.file.Files.createDirectories(tree.resolve("child"));
        java.nio.file.Files.writeString(tree.resolve("child").resolve("leaf.txt"), "leaf");
        assertThat(org.jboss.util.file.Files.delete(tree.toFile())).isTrue();
        assertThat(tree).doesNotExist();
    }
}
