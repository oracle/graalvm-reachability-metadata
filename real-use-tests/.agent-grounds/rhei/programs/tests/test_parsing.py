# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Unit tests for the parsing the real-use workflow programs rely on.

§FS-real-use-test-generation
"""
from __future__ import annotations

import json
import os
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import local_metadata  # noqa: E402
import run_summary  # noqa: E402
import scenarios  # noqa: E402


class UnitTest(unittest.TestCase):
    """The parsing the programs rely on, checked in isolation."""

    def test_scenarios_are_found_in_the_project_language(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            kotlin: str = os.path.join(tmp, "src", "test", "kotlin")
            package: str = os.path.join(kotlin, "com", "example")
            os.makedirs(package)
            for name, text in (("JsonColumnTest.kt", "package com.example\n\nclass JsonColumnTest\n"),
                               ("Helper.java", "package com.example.other;\n")):
                with open(os.path.join(package, name), "w") as handle:
                    handle.write(text)
            self.assertEqual(kotlin, scenarios.test_source_path(tmp))
            self.assertEqual("Kotlin", scenarios.language(kotlin))
            self.assertEqual([os.path.join(package, "JsonColumnTest.kt")], scenarios.scenario_files(kotlin))
            self.assertEqual(os.path.join(package, "JsonColumnTest.kt"),
                             scenarios.class_file(kotlin, "com.example.JsonColumnTest"))
            self.assertEqual({"com.example"}, scenarios.test_packages(kotlin))
            self.assertEqual(os.path.join(tmp, "x", "src", "test", "java"),
                             scenarios.test_source_path(os.path.join(tmp, "x")))

    def test_java_package_declarations_keep_their_semicolon(self) -> None:
        self.assertEqual("com.example", scenarios.PACKAGE_DECLARATION.search("package com.example;\n").group(1))
        self.assertEqual("com.example", scenarios.PACKAGE_DECLARATION.search("package com.example\n").group(1))

    def test_agent_cost_keeps_input_disjoint_from_cache_and_never_invents_a_zero(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            invocations: str = os.path.join(tmp, "runtime", "accounting", "invocations")
            os.makedirs(invocations)
            records: dict = {
                "a": {"token_convention": "input-total-includes-cache",
                      "tokens": {"input": {"total": {"value": 110}, "cached_read": {"value": 100}},
                                 "output": {"total": {"value": 5}}}},
                "b": {"token_convention": "input-total-excludes-cache",
                      "tokens": {"input": {"total": {"value": 7}, "cached_read": {"value": 50}},
                                 "output": {"total": {"value": 3}}}},
                # Killed before it reported usage: no tokens.
                "c": {"tokens": {"input": {"total": {"status": "missing"}}}},
            }
            for name, record in records.items():
                with open(os.path.join(invocations, f"{name}.json"), "w") as handle:
                    json.dump(record, handle)
            tokens = run_summary.agent_tokens(tmp)
        self.assertEqual({"input": 17, "cached_input": 150, "cache_write_input": None, "output": 8}, tokens)

    def test_library_coverage_reads_the_report_totals(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            report: str = os.path.join(tmp, "jacocoTestReport.xml")
            with open(report, "w") as handle:
                handle.write('<report name="lib"><counter type="LINE" missed="0" covered="0"/></report>')
            self.assertEqual({"line": {"covered": 0, "total": 0, "ratio": 1.0}, "instruction": "N/A",
                              "method": "N/A"}, run_summary.library_coverage(report))

    def test_copy_collected_keeps_conforming_new_entries_only(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            kept: dict = {"type": "a.b.Open", "condition": {"typeReached": "a.b.Client"}}
            collected: str = os.path.join(tmp, "collected.json")
            with open(collected, "w") as handle:
                json.dump({"reflection": [kept, {"type": "a.b.Bare"},
                                          {"type": "c.D", "condition": {"typeReached": "c.Outside"}}]}, handle)
            target: str = os.path.join(tmp, "out", "reachability-metadata.json")
            self.assertEqual(1, local_metadata.copy_collected(collected, target, ["a.b."], set()))
            self.assertEqual(0, local_metadata.copy_collected(collected, target, ["a.b."], set()))
            with open(target) as handle:
                self.assertEqual({"reflection": [kept]}, json.load(handle))


if __name__ == "__main__":
    unittest.main()
