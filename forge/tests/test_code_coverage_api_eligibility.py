# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import csv
import os
import subprocess
import tempfile
import unittest

from code_coverage_rank_test_utils import EXTRACTOR, _graph, _jacoco, _java_tool, _typed_graph
from utility_scripts import code_coverage_api_rank as rank_module
from utility_scripts.code_coverage_api_eligibility import eligible_targets, load_type_model


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
        model = load_type_model(graph_dir)
        self.assertIsNotNone(model)
        return eligible_targets(model)

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
        model = load_type_model(cls._graph_dir)
        assert model is not None, "extractor must emit types.csv and isStatic"
        cls._model = model
        cls._eligible = eligible_targets(model)

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

    def test_extractor_records_method_line_number_tables(self) -> None:
        methods_path = os.path.join(self._graph_dir, "methods.csv")
        with open(methods_path, encoding="utf-8", newline="") as handle:
            methods = {row["id"]: row for row in csv.DictReader(handle)}

        constructor = methods["com.example.Factory#<init>():void"]
        self.assertRegex(constructor["lineNumbers"], r"^\d+:\d+(;\d+:\d+)*$")

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
