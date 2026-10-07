# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Tests for prompt locations resolved against the library source root.

§AR-code-coverage-deep-navigation.3.2
"""

import os
import shutil
import tempfile
import unittest

from utility_scripts import code_coverage_profile_report as report_module
from utility_scripts.code_coverage_model import MethodRef
from utility_scripts.code_coverage_profile_graph import CallGraph
from utility_scripts.code_coverage_profile_render import (
    SourceRoot,
    classification_lines,
    write_markdown,
)

from tests.code_coverage_profile_support import _coverage, _sampled_at

DATABASE: str = "org/h2/engine/Database.java"
CLASSIFICATION: dict = {
    "kind": "fork-not-taken",
    "target": {"sourcePath": DATABASE, "line": 322, "mi": 3, "ci": 0, "mb": 0, "cb": 0},
    "fork": {"sourcePath": DATABASE, "line": 313, "mi": 0, "ci": 9, "mb": 2, "cb": 2},
}


class SourceRootTests(unittest.TestCase):

    def setUp(self) -> None:
        self.root: str = tempfile.mkdtemp(prefix="source-root-")
        self.addCleanup(shutil.rmtree, self.root, True)

    def _add_source(self, source_path: str) -> None:
        path: str = os.path.join(self.root, source_path)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8") as source_file:
            source_file.write("class Database {}\n")

    def test_a_file_under_the_root_is_named_by_its_source_path(self) -> None:
        self._add_source(DATABASE)
        sources: SourceRoot = SourceRoot(self.root)

        lines: list[str] = classification_lines(CLASSIFICATION, sources=sources)

        self.assertEqual(lines[0], f"  target `{DATABASE}:322` never ran")
        self.assertTrue(lines[1].startswith(f"  fork `{DATABASE}:313` ran"))
        self.assertTrue(sources.resolved)
        self.assertEqual(sources.path, os.path.abspath(self.root))

    def test_a_missing_root_keeps_the_file_name(self) -> None:
        sources: SourceRoot = SourceRoot(os.path.join(self.root, "absent"))

        lines: list[str] = classification_lines(CLASSIFICATION, sources=sources)

        self.assertEqual(lines[0], "  target `Database.java:322` never ran")
        self.assertIsNone(sources.path)
        self.assertFalse(sources.resolved)

    def test_a_file_missing_under_the_root_keeps_the_file_name(self) -> None:
        self._add_source("org/h2/engine/Session.java")
        sources: SourceRoot = SourceRoot(self.root)

        lines: list[str] = classification_lines(CLASSIFICATION, sources=sources)

        self.assertTrue(lines[1].startswith("  fork `Database.java:313` ran"))
        self.assertFalse(sources.resolved)


class PromptRootLineTests(unittest.TestCase):
    """The prompt states the root once, near its top, only when it is used."""

    def setUp(self) -> None:
        self.work: str = tempfile.mkdtemp(prefix="source-root-prompt-")
        self.addCleanup(shutil.rmtree, self.work, True)
        self.root: str = os.path.join(self.work, "extracted")

    def _prompt(self, source_root: str | None) -> str:
        entry = MethodRef("org.h2.Driver", "connect", (), "void")
        target = MethodRef("org.h2.engine.Database", "open", (), "void")
        methods: dict[int, MethodRef] = {1: entry, 2: target}
        graph = CallGraph(
            methods=methods,
            key_to_id={ref.canonical_id: static_id for static_id, ref in methods.items()},
            adjacency={1: [{"caller": 1, "callee": 2, "bci": "1", "is_direct": "true",
                            "kind": "call", "unobserved": False}]},
        )
        report, records = report_module.correlate(
            _sampled_at(1, entry),
            graph,
            {"targets": [{"id": entry.canonical_id, "kind": "method"}]},
            {entry.canonical_id: _coverage(entry, covered=True),
             target.canonical_id: _coverage(target)},
        )
        self.assertEqual([record.target_ref for record in records], [target])
        for note in report["bulkTargets"]:
            note["missClassification"] = CLASSIFICATION
        path: str = os.path.join(self.work, "prompt.md")
        write_markdown(report, records, graph, "com.h2database:h2:2.1.210", 1, path,
                       source_root=source_root)
        with open(path, encoding="utf-8") as prompt_file:
            return prompt_file.read()

    def test_a_resolved_location_states_the_absolute_root_before_the_routes(self) -> None:
        os.makedirs(os.path.join(self.root, "org/h2/engine"))
        with open(os.path.join(self.root, DATABASE), "w", encoding="utf-8") as source_file:
            source_file.write("class Database {}\n")

        markdown: str = self._prompt(self.root)

        root_line: str = (
            f"Library sources: `{os.path.abspath(self.root)}`; the source locations "
            "below are relative to it."
        )
        self.assertEqual(markdown.count(root_line), 1)
        self.assertLess(markdown.index(root_line), markdown.index("## Where the tests go"))
        self.assertIn(f"  fork `{DATABASE}:313`", markdown)

    def test_without_a_resolved_location_no_root_is_stated(self) -> None:
        for source_root in (None, self.root):
            with self.subTest(source_root=source_root):
                markdown: str = self._prompt(source_root)

                self.assertNotIn("Library sources", markdown)
                self.assertIn("  fork `Database.java:313`", markdown)


if __name__ == "__main__":
    unittest.main()
