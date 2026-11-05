# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Tests for `prepare`'s run worktree (§AR-forge-workflow-pipeline.4.6) and what it readies.

A worktree is kept, given up, resumed or left over; prepare warms its dependency cache
and lists its search roots in the run note.
"""
from __future__ import annotations

import contextlib
import io
import json
import os
import subprocess
import unittest
from typing import Any
from unittest import mock

import yaml

from support import TEMPLATE, working_directory
from test_prepare import PrepareCase, git_output, prepare_arguments
from utility_scripts.gradle_environment import (
    GRADLE_MODULES_CACHE_DIR,
    checkout_gradle_home_for_repo,
    gradle_user_home_for_repo,
    read_only_dependency_cache_path,
)

import forge_run  # noqa: E402
import prepare  # noqa: E402
import run_note  # noqa: E402
from utility_scripts.native_image_artifact import NativeImageEligibility  # noqa: E402
from exit_codes import BAD_INPUT, INFRASTRUCTURE, NOT_FOR_NATIVE_IMAGE_RECORDED, OK  # noqa: E402
from run_state import RunState, load, runtime_path, save  # noqa: E402


class PrepareWorktreeTest(PrepareCase):

    def test_the_new_worktree_readies_the_shared_dependency_cache(self) -> None:
        worktrees: list[bool] = []
        self.fake.shared_cache.side_effect = lambda _repo, worktree: worktrees.append(os.path.isdir(worktree))
        self.fake.issue("library-new-request")
        self.assertEqual(OK, self.prepare())
        state = load(self.fake.workspace)
        # The checkout home is warmed from the new worktree (§FS-forge-run-requirements.4).
        self.fake.shared_cache.assert_called_once_with(os.path.realpath(self.fake.repo), state.worktree)
        self.assertEqual([True], worktrees)

    def test_a_shared_cache_failure_is_reported_and_the_run_goes_on(self) -> None:
        self.fake.shared_cache.side_effect = OSError("No space left on device")
        self.fake.issue("library-new-request")
        with contextlib.redirect_stderr(io.StringIO()) as errors:
            self.assertEqual(OK, self.prepare())
        self.assertIn("shared Gradle dependency cache was not prepared: No space left on device", errors.getvalue())

    def search_roots(self) -> list[str]:
        """The lines of the run note's search-roots section."""
        with open(runtime_path(self.fake.workspace, "run.md"), encoding="utf-8") as handle:
            section: str = handle.read().split("## Search roots\n", 1)[1]
        return section.strip().split("\n\n", 1)[0].splitlines()

    def test_the_run_note_lists_only_search_roots_that_exist(self) -> None:
        # A first run has no published cache: its root is left out, never an empty path
        # an agent's walk could turn into `/` (§FS-forge-agent-runtime-selection.1).
        os.environ["GRAALVM_HOME"] = self.fake.graalvm("graalvm-ga")
        self.fake.issue("library-new-request")
        self.assertEqual(OK, self.prepare())
        state = load(self.fake.workspace)
        self.assertEqual([f"- Run workspace: `{os.path.realpath(self.fake.workspace)}`",
                          f"- Worktree: `{state.worktree}`",
                          f"- Main sources: `{run_note.source_context_dir(state)}`",
                          f"- Gradle user home: `{gradle_user_home_for_repo(state.worktree)}`",
                          f"- GraalVM home: `{os.environ['GRAALVM_HOME']}`"], self.search_roots())
        self.assertTrue(os.path.isdir(run_note.source_context_dir(state)))

    def test_the_run_note_lists_a_published_dependency_cache(self) -> None:
        cache: str = read_only_dependency_cache_path(checkout_gradle_home_for_repo(self.fake.repo))
        self.fake.shared_cache.side_effect = (
            lambda _repo, _worktree: os.makedirs(os.path.join(cache, GRADLE_MODULES_CACHE_DIR)))
        self.fake.issue("library-new-request")
        self.assertEqual(OK, self.prepare())
        self.assertIn(f"- Read-only Gradle dependency cache: `{cache}`", self.search_roots())

    def test_every_agent_state_walks_only_from_the_search_roots(self) -> None:
        # Any state an agent runs in can go looking for a file, so each carries the rule
        # (§FS-forge-agent-runtime-selection.1).
        with open(TEMPLATE, encoding="utf-8") as handle:
            states: dict[str, dict[str, Any]] = yaml.safe_load(handle)["states"]
        agents: dict[str, dict[str, Any]] = {name: spec for name, spec in states.items() if "target" in spec}
        self.assertEqual({"setup", "cover", "fix"}, set(agents))
        for name, spec in agents.items():
            with self.subTest(state=name):
                self.assertIn({"name": "run", "path": "runtime/forge/run.md"}, spec["inputs"])
                self.assertIn("Walk or search the file system only from the search roots `{input.run.path}` lists; "
                              "outside them, open only a file whose path you were given. Never walk `/tmp`, "
                              "`$HOME`, or `/`, even to reach one of them",
                              " ".join(spec["instructions"].split()))

    def prepare_in_another_checkout(self) -> RunState:
        """Prepare the coordinate as a launcher in a second checkout of the repository does."""
        checkout: str = os.path.join(self.fake.tmp, "other-checkout")
        subprocess.run(["git", "worktree", "add", "--quiet", "--detach", checkout], cwd=self.fake.repo, check=True)
        workspace: str = os.path.join(checkout, "forge", "runs", "ws")
        os.makedirs(workspace)
        with working_directory(workspace):
            code: int = forge_run.main(prepare_arguments(repo=checkout, worktree_root="../worktrees"))
        self.assertEqual(OK, code)
        return load(workspace)

    def test_a_failed_scaffold_gives_up_its_worktree_and_keeps_its_record(self) -> None:
        # Another run's worktree is never touched; the workspace and its logs stay as the record.
        other: str = os.path.join(self.fake.tmp, "runs", "worktrees", "forge-org.example-other-1.0.0")
        subprocess.run(["git", "worktree", "add", "-q", "--detach", other], cwd=self.fake.repo, check=True)
        self.fake.issue("library-new-request")
        self.fake.control({"scaffoldFails": "unreachable"})
        self.assertEqual(INFRASTRUCTURE, self.prepare())
        self.assert_worktree_given_up()
        self.assertTrue(os.path.isdir(other))
        with open(runtime_path(self.fake.workspace, "logs", "scaffold.log"), encoding="utf-8") as handle:
            self.assertIn("Received status code 502", handle.read())
        self.assertEqual("infrastructure", self.outcome()["kind"])

    def test_a_chunk_without_its_report_gives_up_the_worktree(self) -> None:
        self.fake.supported_library()
        self.fake.issue("library-update-request", extra_labels=("chunked-dynamic-access",))
        self.assertEqual(BAD_INPUT, self.prepare())
        self.assert_worktree_given_up()

    def test_an_artifact_marked_not_for_native_image_gives_up_the_worktree(self) -> None:
        self.fake.write("metadata/org.example/lib/index.json",
                        json.dumps([{"not-for-native-image": True, "reason": "Maven packaging is `klib`."}]))
        self.fake.commit("mark lib as not for native image")
        self.fake.issue("library-update-request")
        self.assertEqual(BAD_INPUT, self.prepare())
        self.assert_worktree_given_up()

    def test_a_crash_after_the_spawn_gives_up_the_worktree(self) -> None:
        self.fake.issue("library-new-request")
        crash: subprocess.CalledProcessError = subprocess.CalledProcessError(1, ["git", "commit"])
        with mock.patch.object(prepare, "checkpoint", side_effect=crash), contextlib.redirect_stderr(io.StringIO()):
            self.assertEqual(INFRASTRUCTURE, self.prepare())
        self.assert_worktree_given_up()

    def test_a_prepare_that_saves_its_state_keeps_its_worktree(self) -> None:
        # Prepared, then resumed in the worktree its state names; the close-out gives it up later.
        self.fake.issue("library-new-request")
        for _ in range(2):
            self.assertEqual(OK, self.prepare())
            self.assertTrue(os.path.isdir(load(self.fake.workspace).worktree))
        self.fake.stop_daemons.assert_not_called()

    def test_a_recorded_non_jvm_artifact_keeps_its_worktree(self) -> None:
        self.fake.issue("library-new-request")
        self.fake.artifact.return_value = NativeImageEligibility(True, "Maven packaging is `klib`, not a JVM JAR.")
        for _ in range(2):
            self.assertEqual(NOT_FOR_NATIVE_IMAGE_RECORDED, self.prepare())
            self.assertTrue(os.path.isdir(load(self.fake.workspace).worktree))
        self.fake.stop_daemons.assert_not_called()

    def test_a_worktree_git_refuses_quotes_git(self) -> None:
        self.fake.issue("library-new-request")
        # Named after the worktree's path, not the coordinate alone
        # (§AR-forge-workflow-pipeline.4.6).
        worktree: str = os.path.realpath(
            os.path.join(self.fake.workspace, "..", "worktrees", "forge-org.example-lib-2.0.0"))
        branch: str = prepare.run_branch(worktree)
        subprocess.run(["git", "worktree", "add", "-q", "-b", branch, os.path.join(self.fake.tmp, "elsewhere")],
                       cwd=self.fake.repo, check=True)
        self.assertEqual(BAD_INPUT, self.prepare())
        reason: str = self.outcome()["reason"]
        self.assertTrue(reason.startswith("prepare: bad input: worktree: Command"), reason)
        self.assertFalse(reason.endswith("."), reason)
        self.assertIn(f"fatal: '{branch}' is already", self.outcome()["detail"])

    def test_a_resumed_prepare_keeps_the_run(self) -> None:
        self.fake.issue("library-new-request")
        self.assertEqual(OK, self.prepare())
        first = load(self.fake.workspace)
        self.assertEqual(OK, self.prepare())
        self.assertEqual((first.checkpoint, first.branch),
                         (load(self.fake.workspace).checkpoint, load(self.fake.workspace).branch))
        self.fake.shared_cache.assert_called_once()

    def test_a_resumed_prepare_keeps_a_branch_named_before_the_worktree_path(self) -> None:
        self.fake.issue("library-new-request")
        self.assertEqual(OK, self.prepare())
        first = load(self.fake.workspace)
        # A run in flight across the rename keeps the branch its state records.
        legacy: str = "rhei/forge-org.example-lib-2.0.0"
        subprocess.run(["git", "branch", "-m", first.branch, legacy], cwd=first.worktree, check=True)
        first.branch = legacy
        save(self.fake.workspace, first)
        self.assertEqual(OK, self.prepare())
        resumed = load(self.fake.workspace)
        self.assertEqual((first.checkpoint, legacy), (resumed.checkpoint, resumed.branch))
        self.assertEqual(legacy, git_output(resumed.worktree, "branch", "--show-current"))

    def test_a_leftover_run_branch_restarts_from_the_pinned_base(self) -> None:
        self.fake.issue("library-new-request")
        self.assertEqual(OK, self.prepare())
        first = load(self.fake.workspace)
        # The launcher retires the workspace; the run branch and its worktree may outlive it.
        os.remove(runtime_path(self.fake.workspace, "state.json"))
        self.fake.commit("master moved on")
        self.assertEqual(OK, self.prepare())
        second = load(self.fake.workspace)
        self.assertEqual((first.worktree, first.branch), (second.worktree, second.branch))
        self.assertNotEqual(first.base_commit, second.base_commit)
        self.assertEqual(second.base_commit, git_output(second.worktree, "rev-parse", "HEAD~1"))
        self.assertNotEqual(0, subprocess.run(["git", "merge-base", "--is-ancestor", first.checkpoint, "HEAD"],
                                              cwd=second.worktree).returncode)

    def test_a_leftover_on_a_branch_named_before_the_worktree_path_is_removed(self) -> None:
        self.fake.issue("library-new-request")
        self.assertEqual(OK, self.prepare())
        first = load(self.fake.workspace)
        legacy: str = "rhei/forge-org.example-lib-2.0.0"
        subprocess.run(["git", "branch", "-m", first.branch, legacy], cwd=first.worktree, check=True)
        os.remove(runtime_path(self.fake.workspace, "state.json"))
        self.assertEqual(OK, self.prepare())
        second = load(self.fake.workspace)
        self.assertEqual((first.worktree, first.branch), (second.worktree, second.branch))
        self.assertEqual(first.branch, git_output(second.worktree, "branch", "--show-current"))

    def test_another_checkouts_run_worktree_neither_blocks_the_run_nor_is_touched(self) -> None:
        """Checkouts share branches, not run worktrees (§AR-forge-workflow-pipeline.4.6)."""
        self.fake.issue("library-new-request")
        other: RunState = self.prepare_in_another_checkout()
        self.fake.commit("master moved on")
        self.assertEqual(OK, self.prepare())
        own = load(self.fake.workspace)
        self.assertEqual(os.path.basename(other.worktree), os.path.basename(own.worktree))
        self.assertNotEqual(other.branch, own.branch)
        self.assertNotEqual(other.base_commit, own.base_commit)
        # The other launcher's worktree, stale or live, is exactly as it left it.
        self.assertEqual(other.branch, git_output(other.worktree, "branch", "--show-current"))
        self.assertEqual(other.checkpoint, git_output(other.worktree, "rev-parse", "HEAD"))
        self.assertEqual(other.checkpoint, git_output(self.fake.repo, "rev-parse", other.branch))
        self.assertEqual("", git_output(other.worktree, "status", "--porcelain", "--untracked-files=no"))


if __name__ == "__main__":
    unittest.main()
