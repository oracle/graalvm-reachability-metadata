/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_googlecode_concurrent_trees.concurrent_trees;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.googlecode.concurrenttrees.common.CharSequences;
import com.googlecode.concurrenttrees.common.KeyValuePair;
import com.googlecode.concurrenttrees.common.PrettyPrinter;
import com.googlecode.concurrenttrees.radix.ConcurrentRadixTree;
import com.googlecode.concurrenttrees.radix.RadixTree;
import com.googlecode.concurrenttrees.radix.node.NodeFactory;
import com.googlecode.concurrenttrees.radix.node.concrete.DefaultCharArrayNodeFactory;
import com.googlecode.concurrenttrees.radixinverted.ConcurrentInvertedRadixTree;
import com.googlecode.concurrenttrees.radixinverted.InvertedRadixTree;
import com.googlecode.concurrenttrees.radixreversed.ConcurrentReversedRadixTree;
import com.googlecode.concurrenttrees.radixreversed.ReversedRadixTree;
import com.googlecode.concurrenttrees.solver.LCSubstringSolver;
import com.googlecode.concurrenttrees.suffix.ConcurrentSuffixTree;
import com.googlecode.concurrenttrees.suffix.SuffixTree;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Concurrent_treesTest {
    private static final NodeFactory NODE_FACTORY = new DefaultCharArrayNodeFactory();

    @Test
    void radixTreeStoresUpdatesRemovesAndLooksUpByPrefix() {
        RadixTree<Integer> tree = new ConcurrentRadixTree<>(NODE_FACTORY);

        assertThat(tree.put("romane", 1)).isNull();
        assertThat(tree.put("romanus", 2)).isNull();
        assertThat(tree.put("rubens", 3)).isNull();
        assertThat(tree.put("ruber", 4)).isNull();
        assertThat(tree.put("rubicon", 5)).isNull();
        assertThat(tree.put("rubicundus", 6)).isNull();
        assertThat(tree.size()).isEqualTo(6);

        assertThat(tree.getValueForExactKey("romanus")).isEqualTo(2);
        assertThat(tree.getValueForExactKey("roma")).isNull();
        assertThat(tree.put("rubens", 30)).isEqualTo(3);
        assertThat(tree.putIfAbsent("rubens", 300)).isEqualTo(30);
        assertThat(tree.putIfAbsent("ruby", 7)).isNull();
        assertThat(tree.size()).isEqualTo(7);

        assertThat(strings(tree.getKeysStartingWith("rub")))
                .containsExactlyInAnyOrder("rubens", "ruber", "rubicon", "rubicundus", "ruby");
        assertThat(integers(tree.getValuesForKeysStartingWith("roma")))
                .containsExactlyInAnyOrder(1, 2);
        assertThat(pairsByKey(tree.getKeyValuePairsForKeysStartingWith("rubi")))
                .containsExactly(Map.entry("rubicon", 5), Map.entry("rubicundus", 6));

        assertThat(tree.remove("romanus")).isTrue();
        assertThat(tree.remove("missing")).isFalse();
        assertThat(tree.getValueForExactKey("romanus")).isNull();
        assertThat(tree.size()).isEqualTo(6);
        assertThat(strings(tree.getKeysStartingWith("roman"))).containsExactly("romane");
    }

    @Test
    void radixTreeFindsClosestKeysWhenSearchTermDivergesInsideTheTree() {
        RadixTree<String> tree = new ConcurrentRadixTree<>(NODE_FACTORY);
        tree.put("FORD", "make");
        tree.put("FORD FIESTA", "small car");
        tree.put("FORD FOCUS", "family car");
        tree.put("FORD MUSTANG", "sports car");
        tree.put("FOSTER", "name");

        assertThat(strings(tree.getClosestKeys("FORD FUSION")))
                .containsExactlyInAnyOrder("FORD FIESTA", "FORD FOCUS");
        assertThat(strings(tree.getClosestKeys("FORD M"))).containsExactly("FORD MUSTANG");
        assertThat(strings(tree.getClosestKeys("FO")))
                .containsExactlyInAnyOrder("FORD", "FORD FIESTA", "FORD FOCUS", "FORD MUSTANG", "FOSTER");
        assertThat(strings(tree.getClosestKeys("TESLA"))).isEmpty();

        assertThat(strings(tree.getValuesForClosestKeys("FORD FUSION")))
                .containsExactlyInAnyOrder("small car", "family car");
        assertThat(pairsByKey(tree.getKeyValuePairsForClosestKeys("FORD FUSION")))
                .containsExactly(Map.entry("FORD FIESTA", "small car"), Map.entry("FORD FOCUS", "family car"));
    }

    @Test
    void prettyPrinterRendersTreeStructureAndValues() {
        ConcurrentRadixTree<String> tree = new ConcurrentRadixTree<>(NODE_FACTORY);
        tree.put("team", "group");
        tree.put("tea", "drink");
        tree.put("to", "direction");
        tree.put("ton", "weight");

        String expected = """
                \u25CB
                \u2514\u2500\u2500 \u25CB t
                    \u251C\u2500\u2500 \u25CB ea (drink)
                    \u2502   \u2514\u2500\u2500 \u25CB m (group)
                    \u2514\u2500\u2500 \u25CB o (direction)
                        \u2514\u2500\u2500 \u25CB n (weight)
                """;

        assertThat(PrettyPrinter.prettyPrint(tree)).isEqualTo(expected);
        StringBuilder rendered = new StringBuilder();
        PrettyPrinter.prettyPrint(tree, rendered);
        assertThat(rendered).hasToString(expected);
    }

    @Test
    void charSequencesGenerateAndTransformSubsequences() {
        assertThat(strings(CharSequences.generatePrefixes("catalog")))
                .containsExactly("c", "ca", "cat", "cata", "catal", "catalo", "catalog");
        assertThat(strings(CharSequences.generateSuffixes("catalog")))
                .containsExactly("catalog", "atalog", "talog", "alog", "log", "og", "g");

        assertThat(CharSequences.getCommonPrefix("catalog", "caterpillar").toString()).isEqualTo("cat");
        assertThat(CharSequences.getPrefix("catalog", 4).toString()).isEqualTo("cata");
        assertThat(CharSequences.getSuffix("catalog", 3).toString()).isEqualTo("alog");
        assertThat(CharSequences.subtractPrefix("catalog", "cat").toString()).isEqualTo("alog");
        assertThat(CharSequences.concatenate("cata", "log").toString()).isEqualTo("catalog");
        assertThat(CharSequences.reverse("catalog").toString()).isEqualTo("golatac");
        assertThat(CharSequences.fromCharArray(new char[] {'t', 'r', 'i', 'e'}).toString()).isEqualTo("trie");
        assertThat(CharSequences.toCharArray(new StringBuilder("trie"))).containsExactly('t', 'r', 'i', 'e');
        assertThat(CharSequences.toString(new StringBuilder("trie"))).isEqualTo("trie");
    }

    @Test
    void invertedRadixTreeFindsPrefixesAndContainedKeys() {
        InvertedRadixTree<String> tree = new ConcurrentInvertedRadixTree<>(NODE_FACTORY);
        tree.put("/api", "api");
        tree.put("/api/users", "users");
        tree.put("/api/users/admin", "admins");
        tree.put("/assets", "assets");
        tree.put("users", "bare-users");

        assertThat(strings(tree.getKeysPrefixing("/api/users/admin/42")))
                .containsExactlyInAnyOrder("/api", "/api/users", "/api/users/admin");
        assertThat(strings(tree.getValuesForKeysPrefixing("/api/users/admin/42")))
                .containsExactlyInAnyOrder("api", "users", "admins");
        assertThat(pairsByKey(tree.getKeyValuePairsForKeysPrefixing("/api/users/admin/42")))
                .containsExactly(
                        Map.entry("/api", "api"),
                        Map.entry("/api/users", "users"),
                        Map.entry("/api/users/admin", "admins"));

        assertThat(tree.getLongestKeyPrefixing("/api/users/admin/42").toString()).isEqualTo("/api/users/admin");
        assertThat(tree.getValueForLongestKeyPrefixing("/api/users/7")).isEqualTo("users");
        KeyValuePair<String> longestPair = tree.getKeyValuePairForLongestKeyPrefixing("/assets/logo.png");
        assertThat(longestPair.getKey().toString()).isEqualTo("/assets");
        assertThat(longestPair.getValue()).isEqualTo("assets");

        assertThat(strings(tree.getKeysContainedIn("request /api/users returned all records successfully")))
                .containsExactlyInAnyOrder("/api", "/api/users", "users");
        assertThat(strings(tree.getValuesForKeysContainedIn("request /api/users returned all records successfully")))
                .containsExactlyInAnyOrder("api", "users", "bare-users");
        assertThat(pairsByKey(tree.getKeyValuePairsForKeysContainedIn("request /assets contains no users")))
                .containsExactly(
                        Map.entry("/assets", "assets"),
                        Map.entry("users", "bare-users"));
    }

    @Test
    void reversedRadixTreeFindsKeysBySuffix() {
        ReversedRadixTree<String> tree = new ConcurrentReversedRadixTree<>(NODE_FACTORY);
        tree.put("report.txt", "text report");
        tree.put("summary.txt", "text summary");
        tree.put("archive.tar.gz", "compressed archive");
        tree.put("image.png", "image");

        assertThat(tree.getValueForExactKey("image.png")).isEqualTo("image");
        assertThat(strings(tree.getKeysEndingWith(".txt"))).containsExactlyInAnyOrder("report.txt", "summary.txt");
        assertThat(strings(tree.getValuesForKeysEndingWith(".gz"))).containsExactly("compressed archive");
        assertThat(pairsByKey(tree.getKeyValuePairsForKeysEndingWith(".png")))
                .containsExactly(Map.entry("image.png", "image"));

        assertThat(tree.remove("summary.txt")).isTrue();
        assertThat(tree.getValueForExactKey("summary.txt")).isNull();
        assertThat(strings(tree.getKeysEndingWith(".txt"))).containsExactly("report.txt");
        assertThat(tree.size()).isEqualTo(3);
    }

    @Test
    void suffixTreeFindsKeysBySuffixAndSubstring() {
        SuffixTree<String> tree = new ConcurrentSuffixTree<>(NODE_FACTORY);
        tree.put("banana", "yellow fruit");
        tree.put("bandana", "headwear");
        tree.put("ananas", "pineapple");
        tree.put("cab", "taxi");

        assertThat(tree.getValueForExactKey("banana")).isEqualTo("yellow fruit");
        assertThat(strings(tree.getKeysEndingWith("ana"))).containsExactlyInAnyOrder("banana", "bandana");
        assertThat(strings(tree.getValuesForKeysEndingWith("nas"))).containsExactly("pineapple");
        assertThat(pairsByKey(tree.getKeyValuePairsForKeysEndingWith("ana")))
                .containsExactly(Map.entry("banana", "yellow fruit"), Map.entry("bandana", "headwear"));

        assertThat(strings(tree.getKeysContaining("ban"))).containsExactlyInAnyOrder("banana", "bandana");
        assertThat(strings(tree.getValuesForKeysContaining("ban")))
                .containsExactlyInAnyOrder("yellow fruit", "headwear");
        assertThat(pairsByKey(tree.getKeyValuePairsForKeysContaining("ab")))
                .containsExactly(Map.entry("cab", "taxi"));

        assertThat(tree.remove("cab")).isTrue();
        assertThat(strings(tree.getKeysContaining("ab"))).isEmpty();
        assertThat(tree.size()).isEqualTo(3);
    }

    @Test
    void longestCommonSubstringSolverTracksCommonTextAcrossInputs() {
        LCSubstringSolver solver = new LCSubstringSolver(NODE_FACTORY);

        assertThat(solver.add("xxabcdefyy")).isTrue();
        assertThat(solver.getLongestCommonSubstring().toString()).isEqualTo("xxabcdefyy");
        assertThat(solver.add("zzabcdeqq")).isTrue();
        assertThat(solver.getLongestCommonSubstring().toString()).isEqualTo("abcde");
        assertThat(solver.add("12xabcd34")).isTrue();
        assertThat(solver.getLongestCommonSubstring().toString()).isEqualTo("abcd");
        assertThat(solver.add("UVWXYZ")).isTrue();
        assertThat(solver.getLongestCommonSubstring().toString()).isEmpty();
    }

    private static List<String> strings(Iterable<? extends CharSequence> iterable) {
        List<String> result = new ArrayList<>();
        for (CharSequence item : iterable) {
            result.add(item.toString());
        }
        return result;
    }

    private static List<Integer> integers(Iterable<Integer> iterable) {
        List<Integer> result = new ArrayList<>();
        for (Integer item : iterable) {
            result.add(item);
        }
        return result;
    }

    private static <T> Map<String, T> pairsByKey(Iterable<KeyValuePair<T>> iterable) {
        Map<String, T> result = new TreeMap<>();
        for (KeyValuePair<T> pair : iterable) {
            result.put(pair.getKey().toString(), pair.getValue());
        }
        return result;
    }
}
