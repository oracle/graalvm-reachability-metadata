# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import os
import shutil
import subprocess
import tempfile
import unittest

from utility_scripts import code_coverage_api_rank as rank_module

EXTRACTOR = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "utility_scripts", "java", "CallGraphExtractor.java",
)

# `entry` delegates to `core`, and `overload` delegates to `entry`, so the two
# public entries reach an identical internal set. `lonely` reaches nothing, and
# `viaLambda` is only reachable through an invokedynamic bootstrap target.
_LIBRARY_SRC = """package com.example;

import java.util.function.Supplier;

public class Library {
    public int entry(String text) { return core(text); }
    public int overload(String text, int unused) { return entry(text); }
    public int lonely() { return 0; }
    public int lambdaHolder() { return supplier().get(); }
    private Supplier<Integer> supplier() { return () -> viaLambda(); }
    private int core(String text) { return helper(text) + 1; }
    private int helper(String text) { return text.length(); }
    private int viaLambda() { return 7; }
}
"""


def _java_tool(name: str) -> str | None:
    home = os.environ.get("GRAALVM_HOME") or os.environ.get("JAVA_HOME")
    if home:
        candidate = os.path.join(home, "bin", name)
        if os.path.isfile(candidate):
            return candidate
    return shutil.which(name)


def _graph(directory: str, methods: list[tuple[str, bool]], edges: list[tuple[str, str]]) -> str:
    os.makedirs(directory, exist_ok=True)
    with open(os.path.join(directory, "methods.csv"), "w", encoding="utf-8") as handle:
        handle.write("id,hasCode,isPublicApi\n")
        for method_id, has_code in methods:
            handle.write(f'"{method_id}",{str(has_code).lower()},false\n')
    with open(os.path.join(directory, "edges.csv"), "w", encoding="utf-8") as handle:
        handle.write("caller,callee\n")
        for caller, callee in edges:
            handle.write(f'"{caller}","{callee}"\n')
    return directory


def _jacoco(path: str, methods: list[tuple[str, str, str, bool]]) -> str:
    """Write a minimal JaCoCo XML report from (owner, name, desc, covered)."""
    lines: list[str] = ['<?xml version="1.0" encoding="UTF-8" standalone="yes"?>', '<report name="rank">']
    owners: dict[str, list[tuple[str, str, bool]]] = {}
    for owner, name, desc, covered in methods:
        owners.setdefault(owner, []).append((name, desc, covered))
    for owner, entries in owners.items():
        package, _, simple = owner.rpartition("/")
        lines.append(f'  <package name="{package}">')
        lines.append(f'    <class name="{owner}" sourcefilename="{simple}.java">')
        for name, desc, covered in entries:
            hit, miss = (1, 0) if covered else (0, 1)
            lines.append(f'      <method name="{name}" desc="{desc}" line="1">')
            lines.append(f'        <counter type="METHOD" missed="{miss}" covered="{hit}"/>')
            lines.append("      </method>")
        lines.append("    </class>")
        lines.append("  </package>")
    lines.append("</report>")
    with open(path, "w", encoding="utf-8") as handle:
        handle.write("\n".join(lines) + "\n")
    return path


class RankUniverseTests(unittest.TestCase):
    """The universe includes public entries, so every candidate is worth >= 1.

    `delegate` calls `entry`, which calls the internal `helper`; `lonely` calls
    nothing; `bodiless` has no bytecode; `done` is already covered.
    """

    OWNER = "com/example/Api"
    ENTRY = "com.example.Api#entry():void"
    LONELY = "com.example.Api#lonely():void"
    DELEGATE = "com.example.Api#delegate():void"
    BODILESS = "com.example.Api#bodiless():void"
    DONE = "com.example.Api#done():void"
    HELPER = "com.example.Internal#helper():void"

    def _rank(self, directory: str) -> dict:
        graph_dir = _graph(
            os.path.join(directory, "graph"),
            [
                (self.ENTRY, True), (self.LONELY, True), (self.DELEGATE, True),
                (self.BODILESS, False), (self.DONE, True), (self.HELPER, True),
            ],
            [(self.DELEGATE, self.ENTRY), (self.ENTRY, self.HELPER)],
        )
        jacoco = _jacoco(os.path.join(directory, "jacoco.xml"), [
            (self.OWNER, "done", "()V", True),
            (self.OWNER, "entry", "()V", False),
        ])
        inventory = {"coordinate": "g:a:1", "targets": [
            {"id": self.ENTRY}, {"id": self.LONELY}, {"id": self.DELEGATE},
            {"id": self.BODILESS}, {"id": self.DONE},
        ]}
        return rank_module.rank(graph_dir, inventory, [jacoco], 10)

    def test_entry_that_reaches_nothing_still_scores_one(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            report = self._rank(directory)
        selected = {target["id"]: target["unlocks"] for target in report["targets"]}
        # Without its own bit `lonely` would score zero and stop the greedy pass,
        # dropping every remaining uncovered public method from the prompt.
        self.assertEqual(selected.get(self.LONELY), 1)

    def test_delegating_caller_outranks_the_entry_it_calls(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            report = self._rank(directory)
        selected = {target["id"]: target["unlocks"] for target in report["targets"]}
        # `delegate` reaches itself, `entry` and `helper`; testing it covers all
        # three, so `entry` has nothing left to add and is not selected again.
        self.assertEqual(selected[self.DELEGATE], 3)
        self.assertNotIn(self.ENTRY, selected)

    def test_bodiless_entry_is_never_selected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            report = self._rank(directory)
        ids = {target["id"] for target in report["targets"]}
        # Abstract and interface methods carry no bytecode, so JaCoCo can never
        # mark them covered and the agent cannot target them directly.
        self.assertNotIn(self.BODILESS, ids)

    def test_covered_entry_leaves_both_the_candidates_and_the_universe(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            report = self._rank(directory)
        ids = {target["id"] for target in report["targets"]}
        self.assertNotIn(self.DONE, ids)
        # Universe: entry, lonely, delegate, helper — done is covered, bodiless
        # has no code.
        self.assertEqual(report["summary"]["universeMethods"], 4)
        self.assertEqual(report["summary"]["uncoveredCandidates"], 4)


class ReachableSetTests(unittest.TestCase):

    def test_transitive_reach_is_collected_in_one_pass(self) -> None:
        # 0 -> 1 -> 2, with only 1 and 2 in the universe.
        adjacency = [[1], [2], []]
        universe_bit = {1: 1 << 0, 2: 1 << 1}
        reach = rank_module.reachable_sets(adjacency, universe_bit, {0})
        self.assertEqual(reach[0].bit_count(), 2)

    def test_cycles_do_not_stall_propagation(self) -> None:
        # 0 -> 1 -> 2 -> 1, so 1 and 2 form a strongly connected component.
        adjacency = [[1], [2], [1]]
        universe_bit = {1: 1 << 0, 2: 1 << 1}
        reach = rank_module.reachable_sets(adjacency, universe_bit, {0, 1, 2})
        self.assertEqual(reach[0].bit_count(), 2)
        self.assertEqual(reach[1].bit_count(), 2)
        self.assertEqual(reach[2].bit_count(), 2)

    def test_unreachable_node_reaches_nothing(self) -> None:
        adjacency = [[], [2], []]
        reach = rank_module.reachable_sets(adjacency, {2: 1 << 0}, {0})
        self.assertEqual(reach[0], 0)

    def test_deep_chain_does_not_exhaust_the_python_stack(self) -> None:
        # Recursive SCC detection would overflow well before this depth.
        depth = 20000
        adjacency = [[node + 1] for node in range(depth)] + [[]]
        reach = rank_module.reachable_sets(adjacency, {depth: 1 << 0}, {0})
        self.assertEqual(reach[0].bit_count(), 1)


class SelectTargetTests(unittest.TestCase):

    def test_overlapping_candidate_loses_all_marginal_value(self) -> None:
        ids = ["a", "b"]
        # Both candidates reach the same two universe methods.
        reach = {0: 0b11, 1: 0b11}
        selected = rank_module.select_targets([0, 1], reach, 10, ids)
        self.assertEqual(len(selected), 1)
        self.assertEqual(selected[0], (0, 2))

    def test_smaller_candidate_is_kept_when_it_adds_new_coverage(self) -> None:
        ids = ["big", "small"]
        # `big` unlocks three, `small` unlocks one that `big` does not.
        reach = {0: 0b0111, 1: 0b1000}
        selected = rank_module.select_targets([0, 1], reach, 10, ids)
        self.assertEqual([entry[1] for entry in selected], [3, 1])

    def test_lower_bound_candidate_is_not_dropped_by_a_stale_maximum(self) -> None:
        # `wide` looks best initially but overlaps entirely with `first`, while
        # `narrow` still adds value. A premature stop would discard `narrow`.
        ids = ["first", "narrow", "wide"]
        reach = {0: 0b0111, 1: 0b1000, 2: 0b0111}
        selected = rank_module.select_targets([0, 1, 2], reach, 10, ids)
        self.assertEqual([ids[entry[0]] for entry in selected], ["first", "narrow"])

    def test_limit_caps_the_selection(self) -> None:
        ids = [str(number) for number in range(5)]
        reach = {number: 1 << number for number in range(5)}
        selected = rank_module.select_targets(list(range(5)), reach, 2, ids)
        self.assertEqual(len(selected), 2)

    def test_ties_break_on_canonical_id_for_determinism(self) -> None:
        ids = ["z", "a"]
        reach = {0: 0b01, 1: 0b10}
        selected = rank_module.select_targets([0, 1], reach, 1, ids)
        self.assertEqual(ids[selected[0][0]], "a")


class LoadGraphTests(unittest.TestCase):

    def test_missing_graph_is_reported_clearly(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaises(rank_module.ApiRankError) as raised:
                rank_module.load_graph(directory)
            self.assertIn("methods.csv", str(raised.exception))

    def test_edges_naming_unknown_methods_are_ignored(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            _graph(
                directory,
                [("com.example.A#run():void", True)],
                [("com.example.A#run():void", "java.lang.String#length():int")],
            )
            ids, has_code, adjacency = rank_module.load_graph(directory)
            self.assertEqual(ids, ["com.example.A#run():void"])
            self.assertTrue(has_code[ids[0]])
            self.assertEqual(adjacency, [[]])

    def test_node_numbering_follows_sorted_canonical_ids(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            _graph(directory, [("z.Z#a():void", True), ("a.A#a():void", True)], [])
            ids, _, _ = rank_module.load_graph(directory)
            self.assertEqual(ids, ["a.A#a():void", "z.Z#a():void"])


class PromptTests(unittest.TestCase):

    def test_prompt_groups_targets_by_owner_and_shows_unlock_counts(self) -> None:
        report = {
            "summary": {"selected": 2, "uncoveredCandidates": 9, "totalUnlocked": 12},
            "targets": [
                {"id": "com.example.A#one():void", "rank": 1, "unlocks": 10,
                 "reachableUncovered": 10, "behaviorHint": "Public factory."},
                {"id": "com.example.B#two():void", "rank": 2, "unlocks": 2,
                 "reachableUncovered": 5, "behaviorHint": ""},
            ],
        }
        prompt = rank_module.render_prompt(report)
        self.assertIn("## `com.example.A` — 1 targets, unlocks 10", prompt)
        self.assertIn("- `one():void` (unlocks 10) - Public factory.", prompt)
        self.assertIn("- `two():void` (unlocks 2)", prompt)
        # The higher-yield owner must come first so the agent starts there.
        self.assertLess(prompt.index("com.example.A"), prompt.index("com.example.B"))

    def test_prompt_labels_unlock_counts_as_guidance_not_measurement(self) -> None:
        report = {
            "summary": {"selected": 0, "uncoveredCandidates": 0, "totalUnlocked": 0},
            "targets": [],
        }
        self.assertIn("not a coverage measurement", rank_module.render_prompt(report))


@unittest.skipIf(_java_tool("java") is None, "java is required")
@unittest.skipIf(_java_tool("javac") is None, "javac is required")
class ExtractorTests(unittest.TestCase):
    """End-to-end checks that the extractor's ids join with the identity model."""

    @classmethod
    def setUpClass(cls) -> None:
        cls._directory = tempfile.TemporaryDirectory()
        root = cls._directory.name
        source_dir = os.path.join(root, "com", "example")
        os.makedirs(source_dir)
        source = os.path.join(source_dir, "Library.java")
        with open(source, "w", encoding="utf-8") as handle:
            handle.write(_LIBRARY_SRC)
        subprocess.run([_java_tool("javac"), source], cwd=root, check=True)
        jar = os.path.join(root, "library.jar")
        subprocess.run(
            [_java_tool("jar"), "cf", jar, "-C", root, "com"],
            cwd=root, check=True,
        )
        cls._graph_dir = os.path.join(root, "graph")
        subprocess.run(
            [_java_tool("java"), EXTRACTOR, "--output-dir", cls._graph_dir, jar],
            check=True, capture_output=True, text=True,
        )

    @classmethod
    def tearDownClass(cls) -> None:
        cls._directory.cleanup()

    def test_ids_parse_as_canonical_inventory_ids(self) -> None:
        from utility_scripts.code_coverage_model import parse_inventory_id
        ids, _, _ = rank_module.load_graph(self._graph_dir)
        self.assertTrue(ids)
        for method_id in ids:
            self.assertIsNotNone(parse_inventory_id(method_id), method_id)

    def test_private_methods_are_graph_nodes(self) -> None:
        ids, _, _ = rank_module.load_graph(self._graph_dir)
        self.assertIn("com.example.Library#core(java.lang.String):int", ids)
        self.assertIn("com.example.Library#helper(java.lang.String):int", ids)

    def test_delegating_overload_reaches_the_same_internal_set(self) -> None:
        ids, has_code, adjacency = rank_module.load_graph(self._graph_dir)
        index = {method_id: number for number, method_id in enumerate(ids)}
        internal = [
            "com.example.Library#core(java.lang.String):int",
            "com.example.Library#helper(java.lang.String):int",
        ]
        universe_bit = {index[method_id]: 1 << bit for bit, method_id in enumerate(internal)}
        entry = index["com.example.Library#entry(java.lang.String):int"]
        overload = index["com.example.Library#overload(java.lang.String,int):int"]
        lonely = index["com.example.Library#lonely():int"]
        reach = rank_module.reachable_sets(adjacency, universe_bit, {entry, overload, lonely})
        self.assertEqual(reach[entry], reach[overload])
        self.assertEqual(reach[entry].bit_count(), 2)
        self.assertEqual(reach[lonely], 0)
        # The delegating overload must therefore add nothing once entry is taken.
        selected = rank_module.select_targets([entry, overload, lonely], reach, 10, ids)
        self.assertEqual(len(selected), 1)

    def test_lambda_body_is_reachable_through_the_bootstrap_target(self) -> None:
        ids, _, adjacency = rank_module.load_graph(self._graph_dir)
        index = {method_id: number for number, method_id in enumerate(ids)}
        target = "com.example.Library#viaLambda():int"
        self.assertIn(target, index)
        universe_bit = {index[target]: 1}
        holder = index["com.example.Library#lambdaHolder():int"]
        reach = rank_module.reachable_sets(adjacency, universe_bit, {holder})
        self.assertEqual(reach[holder].bit_count(), 1)


if __name__ == "__main__":
    unittest.main()
