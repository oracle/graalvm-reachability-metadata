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

from test_audit import FinalizedRun

from exit_codes import AGENT_WORK, OK, RETURN_AUDIT  # noqa: E402
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
        self.assertIn("- diff-scope gate:", request)
        self.assertIn("forge/notes.md", request)

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
