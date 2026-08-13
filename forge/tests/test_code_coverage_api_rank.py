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


# Eligibility fixture. `Factory` is the only entry point with a public
# constructor. `CircleImpl` and `Palette` have package-private constructors, so
# a test can only ever hold them as `Shape` and `Palette[]` respectively, and
# `Hidden` is reachable by nothing at all.
_ELIGIBILITY_SOURCES = {
    "Shape.java": """package com.example;

public interface Shape {
    double area();
}
""",
    "CircleImpl.java": """package com.example;

public final class CircleImpl implements Shape {
    private final double radius;
    CircleImpl(double radius) { this.radius = radius; }
    public double area() { return 3.14d * radius * radius; }
    public String describe() { return "circle"; }
}
""",
    "BasePalette.java": """package com.example;

public class BasePalette {
    BasePalette() { }
    public String base() { return "base"; }
}
""",
    "Palette.java": """package com.example;

public final class Palette extends BasePalette {
    Palette() { }
    public String name() { return "palette"; }
}
""",
    "Hidden.java": """package com.example;

public final class Hidden {
    Hidden() { }
    public void secret() { }
    public static void helper() { }
}
""",
    "Factory.java": """package com.example;

public class Factory {
    public Factory() { }
    public static Factory create() { return new Factory(); }
    public Shape circle() { return new CircleImpl(1.0d); }
    public Palette[] palettes() { return new Palette[] { new Palette() }; }
}
""",
}


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


def _typed_graph(
        directory: str,
        methods: list[tuple[str, bool, bool, bool]],
        edges: list[tuple[str, str]],
        types: list[tuple[str, str, str]],
) -> str:
    """Write a graph that carries eligibility inputs.

    `methods` rows are (id, hasCode, isPublicApi, isStatic); `types` rows are
    (name, super, semicolon-joined interfaces).
    """
    os.makedirs(directory, exist_ok=True)
    with open(os.path.join(directory, "methods.csv"), "w", encoding="utf-8") as handle:
        handle.write("id,hasCode,isPublicApi,isStatic\n")
        for method_id, has_code, is_public, is_static in methods:
            handle.write(f'"{method_id}",{str(has_code).lower()},'
                         f'{str(is_public).lower()},{str(is_static).lower()}\n')
    with open(os.path.join(directory, "edges.csv"), "w", encoding="utf-8") as handle:
        handle.write("caller,callee\n")
        for caller, callee in edges:
            handle.write(f'"{caller}","{callee}"\n')
    with open(os.path.join(directory, "types.csv"), "w", encoding="utf-8") as handle:
        handle.write("name,isPublic,super,interfaces\n")
        for name, super_name, interfaces in types:
            handle.write(f'"{name}",true,"{super_name}","{interfaces}"\n')
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
        # `delegate` reaches itself, `entry` and `helper`, so it ranks first.
        self.assertEqual(selected[self.DELEGATE], 3)
        # `entry` keeps its own bit and stays, ranked below `delegate`: static
        # reach resolves dispatch by class hierarchy, so covering `delegate` is
        # not evidence that `entry` executed (§WF-code-coverage-improvement.3.1.1).
        self.assertEqual(selected[self.ENTRY], 1)
        ranks = {target["id"]: target["rank"] for target in report["targets"]}
        self.assertLess(ranks[self.DELEGATE], ranks[self.ENTRY])

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


class EligibilityTests(unittest.TestCase):
    """Receiver obtainability decides prompt membership (§3.1.2).

    `Factory` has a public constructor. It returns `Shape`, whose only
    implementation `CircleImpl` has no public constructor and is returned by
    nothing, and an array of `Palette`, which extends `BasePalette`.
    """

    FACTORY_CTOR = "com.example.Factory#<init>():void"
    FACTORY_CREATE = "com.example.Factory#create():com.example.Factory"
    FACTORY_CIRCLE = "com.example.Factory#circle():com.example.Shape"
    FACTORY_PALETTES = "com.example.Factory#palettes():com.example.Palette[]"
    SHAPE_AREA = "com.example.Shape#area():double"
    CIRCLE_AREA = "com.example.CircleImpl#area():double"
    CIRCLE_DESCRIBE = "com.example.CircleImpl#describe():java.lang.String"
    PALETTE_NAME = "com.example.Palette#name():java.lang.String"
    BASE_NAME = "com.example.BasePalette#base():java.lang.String"
    HIDDEN_SECRET = "com.example.Hidden#secret():void"
    HIDDEN_HELPER = "com.example.Hidden#helper():void"

    METHODS = [
        (FACTORY_CTOR, True, True, False),
        (FACTORY_CREATE, True, True, True),
        (FACTORY_CIRCLE, True, True, False),
        (FACTORY_PALETTES, True, True, False),
        (SHAPE_AREA, False, True, False),
        (CIRCLE_AREA, True, True, False),
        (CIRCLE_DESCRIBE, True, True, False),
        (PALETTE_NAME, True, True, False),
        (BASE_NAME, True, True, False),
        (HIDDEN_SECRET, True, True, False),
        (HIDDEN_HELPER, True, True, True),
    ]
    TYPES = [
        ("com.example.Factory", "java.lang.Object", ""),
        ("com.example.Shape", "", ""),
        ("com.example.CircleImpl", "java.lang.Object", "com.example.Shape"),
        ("com.example.BasePalette", "java.lang.Object", ""),
        ("com.example.Palette", "com.example.BasePalette", ""),
        ("com.example.Hidden", "java.lang.Object", ""),
    ]

    def _eligible(self, directory: str) -> dict[str, str]:
        graph_dir = _typed_graph(os.path.join(directory, "graph"), self.METHODS, [], self.TYPES)
        model = rank_module.load_type_model(graph_dir)
        self.assertIsNotNone(model)
        return rank_module.eligible_targets(model)

    def test_constructor_and_static_need_no_receiver(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            eligible = self._eligible(directory)
        self.assertEqual(eligible[self.FACTORY_CTOR], "constructor")
        self.assertEqual(eligible[self.FACTORY_CREATE], "static")
        # `Hidden` itself is unobtainable, but a static method is still callable.
        self.assertEqual(eligible[self.HIDDEN_HELPER], "static")

    def test_instance_method_on_unobtainable_type_is_ineligible(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            eligible = self._eligible(directory)
        self.assertNotIn(self.HIDDEN_SECRET, eligible)

    def test_return_type_of_a_reachable_method_becomes_obtainable(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            eligible = self._eligible(directory)
        self.assertEqual(eligible[self.FACTORY_CIRCLE], "receiver")
        self.assertEqual(eligible[self.SHAPE_AREA], "receiver")

    def test_array_return_admits_its_element_type(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            eligible = self._eligible(directory)
        # `palettes()` returns `Palette[]`; the test holds an element.
        self.assertEqual(eligible[self.PALETTE_NAME], "receiver")

    def test_supertype_of_an_obtainable_type_is_obtainable(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            eligible = self._eligible(directory)
        # Holding a `Palette` permits every `BasePalette` call on it.
        self.assertEqual(eligible[self.BASE_NAME], "receiver")

    def test_override_of_an_obtainable_supertype_is_eligible(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            eligible = self._eligible(directory)
        # The test holds a `Shape` and dispatch lands here; it never names
        # `CircleImpl`, which has no public constructor and is returned by nothing.
        self.assertEqual(eligible[self.CIRCLE_AREA], "override")

    def test_subtype_reachable_only_by_a_cast_is_not_obtainable(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            eligible = self._eligible(directory)
        # `describe()` is declared on no supertype, so reaching it would need
        # `((CircleImpl) shape).describe()` — not realistic public API usage.
        self.assertNotIn(self.CIRCLE_DESCRIBE, eligible)

    def test_ineligible_entries_never_become_candidates(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            graph_dir = _typed_graph(
                os.path.join(directory, "graph"), self.METHODS, [], self.TYPES)
            jacoco = _jacoco(os.path.join(directory, "jacoco.xml"),
                             [("com/example/Other", "unrelated", "()V", True)])
            inventory = {"coordinate": "g:a:1", "targets": [
                {"id": self.FACTORY_CTOR}, {"id": self.CIRCLE_AREA},
                {"id": self.CIRCLE_DESCRIBE}, {"id": self.HIDDEN_SECRET},
            ]}
            report = rank_module.rank(graph_dir, inventory, [jacoco], 10)
        selected = {target["id"]: target["targetVia"] for target in report["targets"]}
        self.assertEqual(selected[self.FACTORY_CTOR], "constructor")
        self.assertEqual(selected[self.CIRCLE_AREA], "override")
        self.assertNotIn(self.CIRCLE_DESCRIBE, selected)
        self.assertNotIn(self.HIDDEN_SECRET, selected)
        self.assertEqual(report["summary"]["uncoveredEntries"], 4)
        self.assertEqual(report["summary"]["ineligibleEntries"], 2)

    def test_unknown_owner_fails_open(self) -> None:
        outsider = "com.other.Absent#call():void"
        with tempfile.TemporaryDirectory() as directory:
            graph_dir = _typed_graph(
                os.path.join(directory, "graph"),
                self.METHODS + [(outsider, True, True, False)], [], self.TYPES)
            jacoco = _jacoco(os.path.join(directory, "jacoco.xml"),
                             [("com/example/Other", "unrelated", "()V", True)])
            report = rank_module.rank(
                graph_dir, {"coordinate": "g:a:1", "targets": [{"id": outsider}]}, [jacoco], 10)
        # The owner came from a jar outside the graph, so excluding it would be
        # a guess about a hierarchy the extractor never saw.
        self.assertEqual(report["targets"][0]["targetVia"], "unknown-owner")

    def test_graph_without_types_disables_filtering(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            graph_dir = _graph(
                os.path.join(directory, "graph"),
                [("com.example.Hidden#secret():void", True)], [])
            jacoco = _jacoco(os.path.join(directory, "jacoco.xml"),
                             [("com/example/Other", "unrelated", "()V", True)])
            inventory = {"coordinate": "g:a:1",
                         "targets": [{"id": "com.example.Hidden#secret():void"}]}
            report = rank_module.rank(graph_dir, inventory, [jacoco], 10)
        # An older extractor's graph must still rank rather than block everything.
        self.assertEqual(report["summary"]["ineligibleEntries"], 0)
        self.assertEqual(report["targets"][0]["targetVia"], "unfiltered")


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

    def test_candidate_reached_by_a_selected_entry_keeps_its_own_bit(self) -> None:
        # `caller` reaches `callee`, so the old subtraction eliminated `callee`
        # outright. Static reach is a class-hierarchy over-approximation while
        # the score is exact execution, so `callee` stays — ranked last, on the
        # single bit nothing can take from it
        # (§WF-code-coverage-improvement.3.1.1).
        ids = ["caller", "callee"]
        universe_bit = {0: 0b01, 1: 0b10}
        reach = {0: 0b11, 1: 0b10}
        selected = rank_module.select_targets([0, 1], reach, 10, ids, universe_bit)
        self.assertEqual([(ids[node], score) for node, score in selected],
                         [("caller", 2), ("callee", 1)])

    def test_fully_overlapping_candidates_both_survive_on_their_own_bits(self) -> None:
        # Two entries into the same internal method: bit 2 is shared, bits 0
        # and 1 are their own.
        ids = ["a", "b"]
        universe_bit = {0: 0b001, 1: 0b010}
        reach = {0: 0b101, 1: 0b110}
        selected = rank_module.select_targets([0, 1], reach, 10, ids, universe_bit)
        self.assertEqual([score for _, score in selected], [2, 1])

    def test_bodiless_candidate_holds_no_bit_and_is_dropped(self) -> None:
        # An abstract or interface entry has no body, so JaCoCo can never mark
        # it covered and it is the one candidate that may score zero.
        ids = ["concrete", "abstract"]
        universe_bit = {0: 0b1}
        reach = {0: 0b1, 1: 0}
        selected = rank_module.select_targets([0, 1], reach, 10, ids, universe_bit)
        self.assertEqual([ids[node] for node, _ in selected], ["concrete"])

    def test_smaller_candidate_is_kept_when_it_adds_new_coverage(self) -> None:
        ids = ["big", "small"]
        # `big` unlocks three, `small` unlocks one that `big` does not.
        reach = {0: 0b0111, 1: 0b1000}
        selected = rank_module.select_targets([0, 1], reach, 10, ids, {})
        self.assertEqual([entry[1] for entry in selected], [3, 1])

    def test_lower_bound_candidate_is_not_dropped_by_a_stale_maximum(self) -> None:
        # `wide` looks best initially but overlaps entirely with `first`, while
        # `narrow` still adds value. A premature stop would discard `narrow`.
        ids = ["first", "narrow", "wide"]
        reach = {0: 0b0111, 1: 0b1000, 2: 0b0111}
        selected = rank_module.select_targets([0, 1, 2], reach, 10, ids, {})
        self.assertEqual([ids[entry[0]] for entry in selected], ["first", "narrow"])

    def test_limit_caps_the_selection(self) -> None:
        ids = [str(number) for number in range(5)]
        reach = {number: 1 << number for number in range(5)}
        selected = rank_module.select_targets(list(range(5)), reach, 2, ids, {})
        self.assertEqual(len(selected), 2)

    def test_ties_break_on_canonical_id_for_determinism(self) -> None:
        ids = ["z", "a"]
        reach = {0: 0b01, 1: 0b10}
        selected = rank_module.select_targets([0, 1], reach, 1, ids, {})
        self.assertEqual(ids[selected[0][0]], "a")


class ClosureNoteTests(unittest.TestCase):
    """A public entry states the closures it builds but never runs.

    The deep phase drops compiler-owned bodies from its prompt, so when the
    enclosing method is a public entry the note belongs here
    (§WF-code-coverage-improvement.3.2.1).
    """

    OWNER = "com/example/Api"
    ENTRY = "com.example.Api#register():void"
    RUN_BODY = "com.example.Api#lambda$register$0():void"
    DEAD_BODY = "com.example.Api#lambda$register$1():void"
    OTHER = "com.example.Other#lambda$elsewhere$0():void"

    def _report(self, directory: str) -> dict:
        graph_dir = _graph(
            os.path.join(directory, "graph"),
            [(self.ENTRY, True), (self.RUN_BODY, True), (self.DEAD_BODY, True),
             (self.OTHER, True)],
            [(self.ENTRY, self.RUN_BODY), (self.ENTRY, self.DEAD_BODY),
             (self.ENTRY, self.OTHER)],
        )
        jacoco = _jacoco(os.path.join(directory, "jacoco.xml"), [
            (self.OWNER, "lambda$register$0", "()V", True),
            (self.OWNER, "lambda$register$1", "()V", False),
        ])
        inventory = {"coordinate": "g:a:1", "targets": [{"id": self.ENTRY}]}
        return rank_module.rank(graph_dir, inventory, [jacoco], 10)

    def test_only_the_entry_own_closures_are_counted(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            report = self._report(directory)
        target = report["targets"][0]
        self.assertEqual(target["id"], self.ENTRY)
        # `OTHER` is a lambda body of a different class, so it is not this
        # entry's closure even though the entry calls it.
        self.assertEqual(target["closures"], 2)
        self.assertEqual(target["closuresUnexecuted"], 1)

    def test_prompt_tells_the_agent_the_closure_must_be_driven(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            prompt = rank_module.render_prompt(self._report(directory))
        self.assertIn("2 closures of which 1 never run", prompt)


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
        # Production puts the entries in the universe too, so the delegating
        # overload adds no internal reach once `entry` is taken but still holds
        # its own bit: it ranks last and stays in the prompt, as does `lonely`,
        # which reaches nothing at all (§WF-code-coverage-improvement.3.1.1).
        entry_bits = dict(universe_bit)
        for bit, node in enumerate((entry, overload, lonely), start=len(internal)):
            entry_bits[node] = 1 << bit
        reach_with_entries = rank_module.reachable_sets(
            adjacency, entry_bits, {entry, overload, lonely})
        selected = rank_module.select_targets(
            [entry, overload, lonely], reach_with_entries, 10, ids, entry_bits)
        self.assertEqual({ids[node] for node, _ in selected},
                         {ids[entry], ids[overload], ids[lonely]})
        # The caller of the family ranks first; the rest hold one bit each.
        self.assertEqual(selected[0][0], overload)
        self.assertEqual([score for _, score in selected[1:]], [1, 1])

    def test_lambda_body_is_reachable_through_the_bootstrap_target(self) -> None:
        ids, _, adjacency = rank_module.load_graph(self._graph_dir)
        index = {method_id: number for number, method_id in enumerate(ids)}
        target = "com.example.Library#viaLambda():int"
        self.assertIn(target, index)
        universe_bit = {index[target]: 1}
        holder = index["com.example.Library#lambdaHolder():int"]
        reach = rank_module.reachable_sets(adjacency, universe_bit, {holder})
        self.assertEqual(reach[holder].bit_count(), 1)


@unittest.skipIf(_java_tool("java") is None, "java is required")
@unittest.skipIf(_java_tool("javac") is None, "javac is required")
class ExtractorEligibilityTests(unittest.TestCase):
    """The extractor's own output must drive the fixpoint (§3.1.2).

    Compiles the fixture, runs the real extractor, and classifies from the
    `methods.csv`/`types.csv` it wrote — no hand-written CSV in between.
    """

    @classmethod
    def setUpClass(cls) -> None:
        cls._directory = tempfile.TemporaryDirectory()
        root = cls._directory.name
        source_dir = os.path.join(root, "com", "example")
        os.makedirs(source_dir)
        sources: list[str] = []
        for name, text in _ELIGIBILITY_SOURCES.items():
            path = os.path.join(source_dir, name)
            with open(path, "w", encoding="utf-8") as handle:
                handle.write(text)
            sources.append(path)
        subprocess.run([_java_tool("javac"), *sources], cwd=root, check=True)
        jar = os.path.join(root, "library.jar")
        subprocess.run([_java_tool("jar"), "cf", jar, "-C", root, "com"], cwd=root, check=True)
        cls._graph_dir = os.path.join(root, "graph")
        subprocess.run(
            [_java_tool("java"), EXTRACTOR, "--output-dir", cls._graph_dir, jar],
            check=True, capture_output=True, text=True,
        )
        model = rank_module.load_type_model(cls._graph_dir)
        assert model is not None, "extractor must emit types.csv and isStatic"
        cls._model = model
        cls._eligible = rank_module.eligible_targets(model)

    @classmethod
    def tearDownClass(cls) -> None:
        cls._directory.cleanup()

    def test_extractor_records_declared_supertypes(self) -> None:
        self.assertEqual(
            self._model.supertypes["com.example.CircleImpl"],
            ["java.lang.Object", "com.example.Shape"],
        )
        self.assertEqual(
            self._model.supertypes["com.example.Palette"], ["com.example.BasePalette"])

    def test_entry_points_are_eligible(self) -> None:
        self.assertEqual(self._eligible["com.example.Factory#<init>():void"], "constructor")
        self.assertEqual(
            self._eligible["com.example.Factory#create():com.example.Factory"], "static")
        self.assertEqual(
            self._eligible["com.example.Factory#circle():com.example.Shape"], "receiver")

    def test_implementation_is_reached_through_its_interface(self) -> None:
        self.assertEqual(self._eligible["com.example.CircleImpl#area():double"], "override")
        # Declared on no supertype, so only a cast would reach it.
        self.assertNotIn("com.example.CircleImpl#describe():java.lang.String", self._eligible)

    def test_array_element_and_its_supertype_are_obtainable(self) -> None:
        self.assertEqual(
            self._eligible["com.example.Palette#name():java.lang.String"], "receiver")
        self.assertEqual(
            self._eligible["com.example.BasePalette#base():java.lang.String"], "receiver")

    def test_unreachable_type_keeps_only_its_static_method(self) -> None:
        self.assertEqual(self._eligible["com.example.Hidden#helper():void"], "static")
        self.assertNotIn("com.example.Hidden#secret():void", self._eligible)


if __name__ == "__main__":
    unittest.main()
