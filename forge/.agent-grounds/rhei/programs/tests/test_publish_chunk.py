# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""A chunk whose only result is its exhaust record (§FS-forge-chunked-dynamic-access).

An earlier chunk of `org.example:lib:2.0.0` merged with its exhaust report; the
next chunk's cover passes add nothing, so class mode can only exhaust classes.
The programs run in the order `states.yaml` routes them, over the fake
repository of `support`.
"""
from __future__ import annotations

import json
import os
import subprocess
import unittest
from typing import Any
from unittest import mock

from support import COORDINATE, FakeRepository, play_fix, working_directory
from test_prepare import prepare_arguments
from test_workflow import URLS, rendered_body

import forge_run  # noqa: E402
import prepare  # noqa: E402
from exit_codes import AGENT_WORK, NOTHING_TO_DO, OK, RETURN_AUDIT  # noqa: E402
from run_state import RunState, load, read_json, runtime_path  # noqa: E402
from utility_scripts.dynamic_access_exhaust_report import (  # noqa: E402
    EXHAUST_REPORT_FILENAME,
    DynamicAccessExhaustReport,
)

REPORT: str = f"tests/src/org.example/lib/2.0.0/{EXHAUST_REPORT_FILENAME}"
DESCRIPTOR: str = "stats/org.example/lib/2.0.0/forge-publication.json"
CODEC: str = "org.example.lib.Codec"
REGISTRY: str = "org.example.lib.Registry"
SESSION: str = "org.example.lib.Session"


class ChunkPublicationTest(unittest.TestCase):

    def setUp(self) -> None:
        self.fake: FakeRepository = FakeRepository()
        self.addCleanup(self.fake.close)
        # The earlier chunk's pull request merged; resolving it needs GitHub.
        merged: Any = mock.patch.object(prepare, "require_previous_chunk_merged")
        merged.start()
        self.addCleanup(merged.stop)

    def step(self, *argv: str) -> int:
        with working_directory(self.fake.workspace):
            return forge_run.main(list(argv))

    @property
    def state(self) -> RunState:
        return load(self.fake.workspace)

    def git(self, *args: str) -> subprocess.CompletedProcess[str]:
        return subprocess.run(["git", *args], cwd=self.state.worktree, stdout=subprocess.PIPE, text=True)

    def resume(self, uncovered: dict[str, int], passes: str, exhausted: tuple[str, ...] = ()) -> int:
        """Merge a first chunk that completed `Codec` and exhausted `exhausted`, then claim
        the next chunk and return what `analyze` exits.

        `Codec`'s one call is covered by the shipped `CodecTest`; every other class
        has only uncovered calls. Four calls keep the stats ratio exact, so
        regenerating the stats changes nothing.
        """
        classes: dict[str, int] = {CODEC: 1, **uncovered}
        self.assertEqual(4, sum(classes.values()))
        entry: dict[str, Any] = {**FakeRepository.index_entry("2.0.0", ("2.0.0",)), **URLS}
        self.fake.write("metadata/org.example/lib/index.json", json.dumps([entry], indent=2))
        self.fake.supported_files("2.0.0", covered=1, total=4)
        report: DynamicAccessExhaustReport = DynamicAccessExhaustReport.create(COORDINATE, 7)
        report.mark_completed(CODEC)
        for name in exhausted:
            report.mark_exhausted(name)
        report.update_chunk_limits(15, 15)
        report.record_publication_identity("first-chunk", "ai/octocat/first-chunk")
        report.save(os.path.join(self.fake.repo, REPORT))
        self.fake.commit("merge the first chunk")
        self.fake.issue("library-new-request", extra_labels=("chunked-dynamic-access",))
        self.fake.control({"classes": classes})
        self.assertEqual(OK, self.step(*prepare_arguments(passes=passes)))
        self.assertEqual(sorted([CODEC, *exhausted]), self.state.chunk.processed)
        self.assertEqual(NOTHING_TO_DO, self.step("setup-check"))
        return self.step("analyze")

    def explore_without_gain(self) -> int:
        """Measure cover passes that add nothing until exploration ends."""
        self.assertEqual("class", self.state.explore.mode)
        code: int = AGENT_WORK
        while code == AGENT_WORK:
            code = self.step("measure")
        self.assertEqual([], self.state.explore.completed)
        return code

    def publish(self) -> int:
        """`native`, `finalize`, the audit and its fix, then `publish`."""
        self.assertEqual(OK, self.step("native"))
        self.assertEqual(OK, self.step("finalize"))
        self.assertEqual(AGENT_WORK, self.step("audit"))
        play_fix(self.fake.workspace, "fixed")
        self.assertEqual(RETURN_AUDIT, self.step("fix-route"))
        self.assertEqual(OK, self.step("audit"))
        return self.step("publish")

    def changed(self) -> list[str]:
        return self.git("diff", "--name-only", self.state.base_commit, "HEAD").stdout.splitlines()

    def outcome(self) -> str:
        return read_json(runtime_path(self.fake.workspace, forge_run.OUTCOME))["outcome"]

    def test_a_chunk_that_only_exhausts_classes_publishes_its_report(self) -> None:
        self.assertEqual(AGENT_WORK, self.resume({REGISTRY: 2, SESSION: 1}, passes="3"))
        self.assertEqual(NOTHING_TO_DO, self.explore_without_gain())
        self.assertEqual([REGISTRY], self.state.explore.exhausted)

        self.assertEqual(OK, self.publish())
        self.assertEqual("completed", self.outcome())
        self.assertEqual([REPORT], [path for path in self.changed() if path.startswith("tests/src/")])
        report: dict[str, Any] = json.loads(self.git("show", f"HEAD:{REPORT}").stdout)
        descriptor: dict[str, Any] = read_json(self.state.path(DESCRIPTOR))
        self.assertEqual(([CODEC], [REGISTRY]), (report["completedClasses"], report["exhaustedClasses"]))
        self.assertEqual((descriptor["publication_id"], descriptor["branch"]),
                         (report["latestChunkPublicationId"], report["latestChunkBranch"]))
        self.assertEqual((True, False), (descriptor["modifiers"]["chunked_dynamic_access"],
                                         descriptor["modifiers"]["chunk_final"]))
        self.assertEqual([REGISTRY], descriptor["render"]["dynamic_access"]["exhaustedClasses"])
        self.assertIn("Refs: #7", rendered_body(descriptor))
        with open(self.fake.control_path + ".gh.log", encoding="utf-8") as handle:
            self.assertIn("--add-label chunked-dynamic-access", handle.read())

    def test_a_last_chunk_that_only_exhausts_classes_removes_the_report_and_fixes(self) -> None:
        self.assertEqual(AGENT_WORK, self.resume({REGISTRY: 3}, passes="20"))
        self.assertEqual(OK, self.explore_without_gain())
        self.assertEqual([REGISTRY], self.state.explore.exhausted)

        self.assertEqual(OK, self.publish())
        self.assertEqual("completed", self.outcome())
        self.assertEqual([REPORT], [path for path in self.changed() if path.startswith("tests/src/")])
        self.assertNotEqual(0, self.git("cat-file", "-e", f"HEAD:{REPORT}").returncode)
        descriptor: dict[str, Any] = read_json(self.state.path(DESCRIPTOR))
        self.assertTrue(descriptor["modifiers"]["chunk_final"])
        self.assertEqual([REGISTRY], descriptor["render"]["dynamic_access"]["exhaustedClasses"])
        self.assertIn("Fixes: #7", rendered_body(descriptor))

    def test_a_last_chunk_with_no_class_left_removes_the_report_and_fixes(self) -> None:
        # Every class an earlier chunk did not process is covered, so nothing is explored.
        self.assertEqual(NOTHING_TO_DO, self.resume({REGISTRY: 3}, passes="20", exhausted=(REGISTRY,)))

        self.assertEqual(OK, self.publish())
        self.assertEqual("completed", self.outcome())
        self.assertNotEqual(0, self.git("cat-file", "-e", f"HEAD:{REPORT}").returncode)
        descriptor: dict[str, Any] = read_json(self.state.path(DESCRIPTOR))
        self.assertTrue(descriptor["modifiers"]["chunk_final"])
        self.assertIn("Fixes: #7", rendered_body(descriptor))

    def test_a_chunk_that_processed_no_class_has_nothing_to_publish(self) -> None:
        self.assertEqual(AGENT_WORK, self.resume({REGISTRY: 2, SESSION: 1}, passes="2"))
        self.assertEqual(NOTHING_TO_DO, self.explore_without_gain())
        self.assertEqual([], self.state.explore.exhausted)

        self.assertEqual(NOTHING_TO_DO, self.publish())
        self.assertEqual("nothing-to-publish", self.outcome())
        self.assertEqual([], self.changed())


if __name__ == "__main__":
    unittest.main()
