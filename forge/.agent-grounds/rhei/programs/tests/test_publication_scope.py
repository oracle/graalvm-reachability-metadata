# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""What a run publishes: the verified tree, on fresh `master`, inside the contribution's file set.

The gate runs after the rebase, so the pushed tree is the one it verified
(§FS-local-ci-equivalent-verification.2), and an edit outside the file set
fails the scope gate instead of being dropped (§AR-expected-paths).
"""
from __future__ import annotations

import json
import os
import subprocess
import unittest
from typing import Any
from unittest.mock import patch

from support import COORDINATE, PREVIOUS
from test_audit import FinalizedRun
from test_workflow import trusted_publisher

import publish  # noqa: E402
from exit_codes import AGENT_WORK, NOTHING_TO_DO, OK, RETURN_AUDIT  # noqa: E402
from run_state import RunState, read_json  # noqa: E402

OWNER_INDEX: str = "metadata/org.example/owner/index.json"
OWNER_METADATA: str = "metadata/org.example/owner/3.0.0/reachability-metadata.json"


class PublicationScopeTest(FinalizedRun):

    def git(self, *args: str, cwd: str | None = None) -> str:
        return subprocess.run(["git", *args], cwd=cwd or self.state.worktree, check=True,
                              stdout=subprocess.PIPE, text=True).stdout.strip()

    def published(self) -> dict[str, Any]:
        self.assertEqual(RETURN_AUDIT, self.audited())
        self.assertEqual(OK, self.step("audit"))
        self.assertEqual(OK, self.step("publish"))
        state: RunState = self.state
        return read_json(state.path(os.path.join("stats", state.group, state.artifact, state.version,
                                                 "forge-publication.json")))

    def test_the_gate_verifies_the_run_on_fresh_master_and_publish_pushes_that_tree(self) -> None:
        self.repaired()
        self.fake.write("README.md", "master moved\n")
        self.fake.commit("master moves")
        moved: str = self.git("rev-parse", "master", cwd=self.fake.remote)
        descriptor: dict[str, Any] = self.published()
        self.assertEqual(moved, self.state.base_commit)
        self.assertEqual(moved, descriptor["base_commit"])
        self.assertEqual(moved, descriptor["local_ci_verification"]["base_commit"])
        self.assertEqual(moved, self.git("merge-base", "HEAD", moved))

    def test_an_edit_outside_the_file_set_fails_the_scope_gate(self) -> None:
        self.repaired()
        self.write("forge/notes.md", "stray\n")
        self.assertEqual(AGENT_WORK, self.step("finalize"))
        request: str = self.read("fix-request.md")
        self.assertIn("```text\ndiff-scope gate: outside the closed file set: forge/notes.md\n```", request)

    def test_a_repair_is_published_against_the_metadata_version_with_its_rounds(self) -> None:
        # The baseline 1.6.0 is a tested version: only its entry 1.5.0 has metadata, stats, and tests.
        self.repaired("fails-java-run", tested_versions=("1.5.0", "1.6.0"))
        descriptor: dict[str, Any] = self.published()
        summary: dict[str, Any] = descriptor["metrics"]["summary"]
        self.assertEqual(PREVIOUS, descriptor["previous_library"]["coordinates"])
        self.assertEqual("1.5.0", descriptor["render"]["baseline_stats"]["version"])
        # The audit's fix request is the only round (§AR-forge-workflow-pipeline.4).
        self.assertEqual(1, summary["iterations"])
        self.assertEqual(0, summary["previous_library_metadata_entries"])
        self.assertIn("previous_library_coverage_percent", summary)
        metrics: dict[str, dict[str, Any]] = read_json(self.state.path(os.path.join(
            "stats", "org.example", "lib", "2.0.0", "execution-metrics.json")))
        self.assertEqual([PREVIOUS], [entry["previous_library"] for entry in metrics.values()])

    def test_a_native_image_build_repair_publishes_as_its_own_fix(self) -> None:
        # The fourth `fails-*` label takes the repair route and its own PR label (§FS-forge-scope).
        self.repaired("fails-native-image-build")
        descriptor: dict[str, Any] = self.published()
        self.assertEqual(("fixes-native-image-build-fail", "fixes-native-image-build-fail"),
                         (descriptor["task_type"], descriptor["template_type"]))
        self.assertIn("/fixes-native-image-build-fail-org.example-lib-2.0.0-", descriptor["branch"])
        publisher: Any = trusted_publisher()
        title, body = publisher.render_publication(descriptor)
        self.assertTrue(title.startswith("[GenAI] Test fix for org.example:lib:2.0.0 using "))
        self.assertIn("Fixes: #7", body)
        self.assertIn("addressing Native Image build failures", body)
        self.assertEqual(["GenAI", "fixes-native-image-build-fail"],
                         publisher.ROUTE_LABELS[descriptor["task_type"]])

    def test_a_deferred_native_image_run_repair_unblocks_its_follow_up(self) -> None:
        # A repair above the boundary parks its follow-up (§FS-forge-chunked-dynamic-access).
        self.repaired("fails-native-image-run")
        # 16 classes, each a quarter covered by `CodecTest`: above 15, below 1.5.0's goal.
        self.fake.control({"classes": {f"org.example.p{n}.Codec": 4 for n in range(16)}})
        self.assertEqual(NOTHING_TO_DO, self.step("analyze"))
        with patch.object(publish, "ensure_coverage_follow_up_issue", return_value=99) as follow_up:
            descriptor: dict[str, Any] = self.published()
        self.assertEqual((COORDINATE, 7, 16, 15), tuple(follow_up.call_args.kwargs[key] for key in (
            "coordinate", "repair_issue_number", "uncovered_class_count", "class_threshold")))
        self.assertEqual([{"type": "deferred_dynamic_access_coverage", "coordinate": COORDINATE,
                           "uncovered_class_count": 16, "class_threshold": 15, "issue_number": 99}],
                         descriptor["follow_ups"])
        publisher: Any = trusted_publisher()
        title, body = publisher.render_publication(descriptor)
        self.assertTrue(title.startswith("[GenAI] Test fix for org.example:lib:2.0.0 using "))
        # The trailer is what releases the parked issue once the repair merges (§AR-pr-body.3).
        self.assertIn("Refs: #99\nForge-Unblocks-Issue: #99\n", body)
        self.assertEqual(["GenAI", "fixes-native-image-run-fail"],
                         publisher.ROUTE_LABELS[descriptor["task_type"]])

    def test_an_update_of_an_owned_version_is_compared_with_its_pinned_base_state(self) -> None:
        self.repaired("library-update-request", owns_version=True)
        descriptor: dict[str, Any] = self.published()
        render: dict[str, Any] = descriptor["render"]
        self.assertEqual(PREVIOUS, descriptor["previous_library"]["coordinates"])
        # The before side is 2.0.0 on the pinned base, not the previous version (§AR-pr-body).
        self.assertEqual(("2.0.0", 5), (render["baseline_stats"]["version"],
                                        render["baseline_stats"]["dynamicAccess"]["coveredCalls"]))
        self.assertEqual(("tested-version", "2.0.0"), (render["library_update_target"]["match_type"],
                                                       render["library_update_target"]["matched_metadata_version"]))

    def test_routed_owner_metadata_is_published(self) -> None:
        self.fake.write(OWNER_INDEX, json.dumps([{"metadata-version": "3.0.0",
                                                  "allowed-packages": ["org.example.owner"]}]))
        self.fake.write(OWNER_METADATA, json.dumps({"reflection": []}))
        self.fake.commit("host the owner")
        self.repaired()
        self.write(OWNER_METADATA, json.dumps({"reflection": [
            {"type": "org.example.owner.Codec", "condition": {"typeReached": "org.example.owner.Api"}}]}))
        self.published()
        self.assertIn(OWNER_METADATA, self.git("diff", "--name-only", self.state.base_commit, "HEAD").splitlines())


if __name__ == "__main__":
    unittest.main()
