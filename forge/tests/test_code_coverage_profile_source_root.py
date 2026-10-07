# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Tests for prompt locations resolved against the library source root.

§AR-code-coverage-deep-navigation.3.2
"""

import os
import re
import shutil
import sys
import tempfile
import unittest
from unittest import mock

import yaml

from utility_scripts import code_coverage_profile_report as report_module
from utility_scripts.code_coverage_deep_sessions import write_prompts
from utility_scripts.code_coverage_model import MethodRef
from utility_scripts.code_coverage_profile_graph import CallGraph
from utility_scripts.code_coverage_profile_render import (
    SourceRoot,
    classification_lines,
    write_markdown,
)

from tests.code_coverage_profile_support import FIXTURES, JACOCO_PATH, _coverage, _sampled_at

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

    def _correlated(self) -> tuple[dict, list, CallGraph]:
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
        return report, records, graph

    def _add_database(self) -> None:
        os.makedirs(os.path.join(self.root, "org/h2/engine"))
        with open(os.path.join(self.root, DATABASE), "w", encoding="utf-8") as source_file:
            source_file.write("class Database {}\n")

    def _prompt(self, source_root: str | None) -> str:
        report, records, graph = self._correlated()
        path: str = os.path.join(self.work, "prompt.md")
        write_markdown(report, records, graph, "com.h2database:h2:2.1.210", 1, path,
                       source_root=source_root)
        with open(path, encoding="utf-8") as prompt_file:
            return prompt_file.read()

    def test_a_resolved_location_states_the_absolute_root_before_the_routes(self) -> None:
        self._add_database()

        markdown: str = self._prompt(self.root)

        root_line: str = (
            f"Library sources: `{os.path.abspath(self.root)}`; a location with a "
            "directory is relative to it, a bare file name was not found under it."
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


    def test_every_prompt_of_a_pass_states_the_root(self) -> None:
        """The whole-pass prompt and each session prompt resolve on their own."""
        self._add_database()
        report, records, graph = self._correlated()

        sessions: list[dict] = write_prompts(
            report, records, graph, "com.h2database:h2:2.1.210", 1, self.work, self.root,
        )

        prompts: list[str] = ["discovery-report-1.md", *(entry["prompt"] for entry in sessions)]
        self.assertGreater(len(prompts), 1)
        for prompt in prompts:
            with self.subTest(prompt=prompt):
                with open(os.path.join(self.work, prompt), encoding="utf-8") as prompt_file:
                    self.assertIn(f"Library sources: `{self.root}`", prompt_file.read())


class SourceRootWiringTests(unittest.TestCase):
    """`deep-measure` hands the analyzer the root `api-inventory` reads, and the
    analyzer hands it to the prompt writer."""

    def test_the_analyzer_passes_the_root_to_the_prompt_writer(self) -> None:
        work: str = tempfile.mkdtemp(prefix="source-root-cli-")
        self.addCleanup(shutil.rmtree, work, True)
        argv: list[str] = [
            "code_coverage_profile_report.py",
            "--profile", os.path.join(FIXTURES, "near_call_sampling.iprof"),
            "--reports-dir", FIXTURES,
            "--api-inventory", os.path.join(FIXTURES, "near_call_inventory.json"),
            "--jacoco-xml", JACOCO_PATH,
            "--source-root", "/sources/extracted",
            "--coordinate", "com.example:demo:1.0.0",
            "--output-dir", work,
        ]

        with mock.patch.object(sys, "argv", argv), \
                mock.patch.object(report_module, "write_prompts", return_value=[]) as writer:
            report_module.main()

        self.assertEqual(writer.call_args.args[6], "/sources/extracted")

    def test_deep_measure_passes_the_root_api_inventory_reads(self) -> None:
        forge_root: str = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
        states_path: str = os.path.join(
            forge_root, ".agents", "rhei", "templates", "code-coverage-improvement", "states.yaml",
        )
        with open(states_path, encoding="utf-8") as states_file:
            states: dict = yaml.safe_load(re.sub(r"\{\{[^{}]+\}\}", "1", states_file.read()))["states"]
        root_expression: str = (
            'f"{work_path}/local_repositories/source_context/" '
            'f"{group}/{artifact}/{version}/main/extracted"'
        )

        for state in ("api-inventory", "deep-measure"):
            with self.subTest(state=state):
                program: str = " ".join(states[state]["program"].split())
                self.assertIn(root_expression, program)
                self.assertIn('"--source-root", source_root', program)

if __name__ == "__main__":
    unittest.main()
