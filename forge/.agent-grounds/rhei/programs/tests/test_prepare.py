# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Tests for `prepare` (§AR-forge-workflow-pipeline.4): route, worktree, version, chunk, request."""
from __future__ import annotations

import argparse
import contextlib
import io
import json
import os
import subprocess
import unittest
from typing import Any
from unittest import mock

import yaml

from support import COORDINATE, PREVIOUS, TEMPLATE, FakeRepository, working_directory
from utility_scripts.gradle_environment import (
    GRADLE_MODULES_CACHE_DIR,
    checkout_gradle_home_for_repo,
    gradle_user_home_for_repo,
    read_only_dependency_cache_path,
    worktree_gradle_home,
)

import forge_run  # noqa: E402
import issue  # noqa: E402
import prepare  # noqa: E402
import run_note  # noqa: E402
from utility_scripts.native_image_artifact import NativeImageEligibility  # noqa: E402
from exit_codes import BAD_INPUT, INFRASTRUCTURE, NOT_FOR_NATIVE_IMAGE_RECORDED, OK, BadInput  # noqa: E402
from run_state import ROUTE_NEW, ROUTE_REPAIR, ROUTE_UPDATE, RunState, load, read_json, runtime_path, save  # noqa: E402

SETUP_AGENT: str = "claude-code[medium]:sonnet"
WORKER_AGENT: str = "pi[high]:openai-codex/gpt-5.6-luna"


def prepare_arguments(setup_agent: str = SETUP_AGENT, worker_agent: str = WORKER_AGENT,
                      passes: str = "20", repo: str = "../../repo", worktree_root: str = "../worktrees") -> list[str]:
    return ["prepare", "--issue-number", "7", "--setup-agent", setup_agent, "--worker-agent", worker_agent,
            "--bulk-min-classes", "5", "--bulk-passes", "3", "--class-attempts", "3", "--defer-classes", "15",
            "--explore-passes", passes, "--finalize-fixes", "5", "--native-fixes", "3", "--repo-checkout", repo,
            "--worktree-root", worktree_root]


def git_output(cwd: str, *args: str) -> str:
    return subprocess.run(["git", *args], cwd=cwd, check=True, stdout=subprocess.PIPE, text=True).stdout.strip()


def jackson(entries: list[dict[str, Any]]) -> str:
    """An index as the repository's Jackson pretty-printer writes it, `"key" : value`."""
    return json.dumps(entries, indent=2, separators=(",", " : ")) + "\n"


class PrepareTest(unittest.TestCase):

    def setUp(self) -> None:
        self.fake: FakeRepository = FakeRepository()
        self.addCleanup(self.fake.close)

    def prepare(self, *arguments: str) -> int:
        with working_directory(self.fake.workspace):
            return forge_run.main(list(arguments) or prepare_arguments())

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

    def test_new_library_is_scaffolded_on_its_run_branch(self) -> None:
        self.fake.issue("library-new-request")
        self.assertEqual(OK, self.prepare())
        state = load(self.fake.workspace)
        self.assertEqual((ROUTE_NEW, COORDINATE), (state.route, state.coordinate))
        # Named after the worktree's path (§AR-forge-workflow-pipeline.4.6).
        self.assertRegex(state.branch, r"^rhei/forge-org\.example-lib-2\.0\.0-[0-9a-f]{8}$")
        self.assertEqual(state.branch, git_output(state.worktree, "branch", "--show-current"))
        self.assertTrue(os.path.isdir(state.path(state.test_dir)))
        log: str = subprocess.run(["git", "log", "--format=%s", "-1"], cwd=state.worktree, check=True,
                                  stdout=subprocess.PIPE, text=True).stdout.strip()
        self.assertEqual(f"Prepare {COORDINATE}", log)
        with open(runtime_path(self.fake.workspace, "run.md"), encoding="utf-8") as handle:
            note: str = handle.read()
        self.assertIn(f"# {COORDINATE} (issue #7, library-new-request)", note)
        self.assertIn("--agent-command='claude -p --permission-mode bypassPermissions --effort medium --model sonnet'",
                      note)
        with open(os.path.join(self.fake.workspace, "runtime", "worktree-refs", "ws.forge.yaml"), encoding="utf-8") as handle:
            self.assertEqual(f"path: {state.worktree}\n", handle.read())
        with open(state.path("gradle-calls.log"), encoding="utf-8") as handle:
            calls: list[str] = handle.read().splitlines()
        # An idle daemon of an earlier run would carry its build state into the scaffold.
        self.assertEqual("--stop", calls[0])
        self.assertTrue(calls[1].startswith("scaffold"))

    def test_non_jvm_artifact_is_committed_without_a_scaffold(self) -> None:
        """Prepare records only the marker (§FS-not-for-native-image-record.2)."""
        self.fake.issue("library-new-request")
        reason: str = "Maven packaging is `klib`, not a JVM JAR."
        replacement: str = "Use `lib-jvm` if a JVM artifact is available."
        self.fake.artifact.return_value = NativeImageEligibility(True, reason, replacement)

        self.assertEqual(NOT_FOR_NATIVE_IMAGE_RECORDED, self.prepare())
        state = load(self.fake.workspace)
        self.fake.artifact.assert_called_once_with(COORDINATE, strict=True)
        self.assertEqual({"reason": reason, "replacement": replacement}, state.not_for_native_image)
        self.assertTrue(state.index_only)
        self.assertEqual("success", state.local_ci["status"])
        self.assertFalse(os.path.exists(state.path(state.test_dir)))
        self.assertFalse(os.path.exists(state.path(state.metadata_dir)))
        marker: list[dict[str, Any]] = [{"not-for-native-image": True, "reason": reason, "replacement": replacement}]
        with open(state.path(state.index_path), encoding="utf-8") as handle:
            self.assertEqual(json.dumps(marker, indent=2) + "\n", handle.read())
        changed: str = subprocess.run(
            ["git", "diff", "--name-only", state.base_commit, "HEAD"], cwd=state.worktree, check=True,
            stdout=subprocess.PIPE, text=True).stdout.strip()
        self.assertEqual(state.index_path, changed)
        self.assertEqual("", subprocess.run(
            ["git", "status", "--porcelain"], cwd=state.worktree, check=True,
            stdout=subprocess.PIPE, text=True).stdout)
        with open(state.path("gradle-calls.log"), encoding="utf-8") as handle:
            calls: str = handle.read()
        self.assertIn(f"validateIndexFiles -Pcoordinates={COORDINATE}", calls)
        self.assertNotIn("scaffold", calls)
        self.assertNotIn("javaTest", calls)
        self.assertTrue(os.path.isfile(runtime_path(self.fake.workspace, "run.md")))
        with open(os.path.join(self.fake.workspace, "runtime", "worktree-refs", "ws.forge.yaml"),
                  encoding="utf-8") as handle:
            self.assertEqual(f"path: {state.worktree}\n", handle.read())
        self.assertIsNone(forge_run.outcome("prepare", NOT_FOR_NATIVE_IMAGE_RECORDED))
        self.assertEqual(NOT_FOR_NATIVE_IMAGE_RECORDED, self.prepare())
        self.fake.artifact.assert_called_once()

    def test_a_rejected_marker_is_bad_input(self) -> None:
        """Validation rejection reaches a maintainer (§FS-not-for-native-image-record.2)."""
        self.fake.issue("library-new-request")
        self.fake.artifact.return_value = NativeImageEligibility(True, "Maven packaging is `aar`.")
        self.fake.control({"indexFails": True})
        with contextlib.redirect_stderr(io.StringIO()) as output:
            self.assertEqual(BAD_INPUT, self.prepare())
        self.assertIn("validateIndexFiles rejected", output.getvalue())
        self.assertIn("index.json does not validate", output.getvalue())
        self.assertEqual("bad-input", read_json(runtime_path(self.fake.workspace, forge_run.OUTCOME))["kind"])
        # The close-out comment names the rejection (§FS-forge-run-requirements.2).
        self.assertEqual("prepare: bad input: validateIndexFiles rejected the not-for-native-image record",
                         self.outcome()["reason"])
        self.assertIn("index.json does not validate", self.outcome()["detail"])

    def test_an_existing_index_is_never_asked(self) -> None:
        """Existing support excludes a verdict (§FS-not-for-native-image-record.1)."""
        self.fake.supported_library()
        self.fake.issue("library-new-request")
        self.assertEqual(OK, self.prepare())
        self.fake.artifact.assert_not_called()

    def test_a_non_new_route_is_never_asked(self) -> None:
        """Other labels exclude a verdict (§FS-not-for-native-image-record.1)."""
        self.fake.supported_library()
        self.fake.issue("fails-java-run")
        self.assertEqual(OK, self.prepare())
        self.fake.artifact.assert_not_called()

    def test_a_new_library_the_pinned_base_already_lists_is_a_bad_input(self) -> None:
        """No agent re-creates shipped support (§FS-forge-run-requirements.2.2)."""
        for entry, commit in (("2.0.0", "support lib 2.0.0"), ("1.5.0", "support lib 1.5.0")):
            with self.subTest(entry=entry):
                self.setUp()
                self.fake.supported_library(tested_versions=("1.5.0",) if entry == "2.0.0" else ("1.5.0", "2.0.0"))
                if entry == "2.0.0":
                    self.fake.supported_version("2.0.0", covered=5, total=6)
                with open(os.path.join(self.fake.repo, "metadata/org.example/lib/index.json"), "a",
                          encoding="utf-8") as handle:
                    handle.write("\n")
                self.fake.commit("touch the index")
                self.fake.issue("library-new-request")
                self.assertEqual(BAD_INPUT, self.prepare())
                self.assertEqual(f"prepare: bad input: {COORDINATE} is already supported: "
                                 f"metadata/org.example/lib/index.json lists 2.0.0 in its {entry} entry",
                                 self.outcome()["reason"])
                # The commit that added the version, not the latest one to touch the index.
                self.assertRegex(self.outcome()["detail"], rf"^Added by [0-9a-f]{{7,}} {commit}$")
                self.fake.artifact.assert_not_called()
                self.assert_worktree_given_up()

    def test_a_chunk_resumes_on_the_version_its_earlier_chunk_merged(self) -> None:
        """Its exhaust report exempts it even without the label (§FS-forge-run-requirements.2.2)."""
        self.fake.supported_library()
        self.fake.supported_version("2.0.0", covered=5, total=6)
        self.fake.write("tests/src/org.example/lib/2.0.0/dynamic-access-exhaust-report.json", "{}\n")
        self.fake.commit("merge the first chunk")
        self.fake.issue("library-new-request")
        self.assertEqual(OK, self.prepare())

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

    def test_update_adds_the_version_beside_the_supported_one(self) -> None:
        self.fake.supported_library()
        self.fake.issue("library-update-request")
        self.assertEqual(OK, self.prepare())
        state = load(self.fake.workspace)
        self.assertEqual((ROUTE_UPDATE, PREVIOUS, 2, 6),
                         (state.route, state.previous_coordinate, state.goal_covered_calls, state.goal_total_calls))
        self.assertTrue(os.path.isfile(state.path(os.path.join(state.test_dir, "src", "test", "java", "org_example",
                                                               "CodecTest.java"))))
        with open(state.path(state.index_path), encoding="utf-8") as handle:
            versions = [entry["metadata-version"] for entry in json.load(handle)]
        self.assertEqual(["2.0.0", "1.5.0"], versions)
        # The version starts as the copy of 1.5.0, so that copy is the body's before side.
        self.assertEqual(("new-version", "2.0.0", 2), (state.update_target["match_type"],
                                                       state.before["baseline_stats"]["version"],
                                                       state.before["baseline_stats"]["dynamicAccess"]["coveredCalls"]))

    def test_an_update_of_an_owned_version_starts_from_its_pinned_base_state(self) -> None:
        self.fake.supported_library()
        self.fake.supported_version("2.0.0", covered=5, total=6)
        self.fake.issue("library-update-request")
        self.assertEqual(OK, self.prepare())
        state = load(self.fake.workspace)
        # The previous version sets the goal; the version's own state is the body's before side.
        self.assertEqual((PREVIOUS, 2, 6), (state.previous_coordinate, state.goal_covered_calls, state.goal_total_calls))
        self.assertEqual(("2.0.0", 5), (state.before["baseline_stats"]["version"],
                                        state.before["baseline_stats"]["dynamicAccess"]["coveredCalls"]))
        self.assertEqual({"requested_coordinate": COORDINATE, "match_type": "tested-version",
                          "matched_metadata_version": "2.0.0", "matched_test_version": "2.0.0",
                          "resolved_metadata_version": "2.0.0", "resolved_test_version": "2.0.0"},
                         state.update_target)

    def test_a_newer_version_takes_latest_and_the_latest_only_fields_first(self) -> None:
        self.fake.supported_library()
        base: dict[str, Any] = {"latest": True, "auto-update": True, "high-priority": True,
                                **FakeRepository.index_entry("1.5.0", ("1.5.0",))}
        self.fake.write("metadata/org.example/lib/index.json", jackson([base]))
        self.fake.commit("opt lib into the compatibility automation")
        self.fake.issue("fails-java-run")
        self.assertEqual(OK, self.prepare())
        state = load(self.fake.workspace)
        added: dict[str, Any] = {**base, "metadata-version": "2.0.0", "tested-versions": ["2.0.0"]}
        kept: dict[str, Any] = {key: value for key, value in base.items()
                                if key not in ("latest", "auto-update", "high-priority")}
        with open(state.path(state.index_path), encoding="utf-8") as handle:
            # As `addLibraryAsLatestMetadataIndexJson` writes it, so `validateIndexFiles` passes.
            self.assertEqual(jackson([added, kept]), handle.read())

    def test_an_update_beside_a_tested_version_is_measured_against_its_metadata_version(self) -> None:
        # The baseline 1.6.0 has no metadata, stats, or tests of its own; its entry 1.5.0 has.
        self.fake.supported_library(tested_versions=("1.5.0", "1.6.0"))
        self.fake.issue("fails-java-run")
        self.assertEqual(OK, self.prepare())
        state = load(self.fake.workspace)
        self.assertEqual((ROUTE_REPAIR, PREVIOUS, 2, 6),
                         (state.route, state.previous_coordinate, state.goal_covered_calls, state.goal_total_calls))

    def test_a_tested_version_is_measured_against_its_metadata_version(self) -> None:
        self.fake.supported_library(tested_versions=("1.5.0", "1.5.1"))
        self.fake.issue("fails-javac-compile", title="Fails org.example:lib:1.5.1")
        self.assertEqual(OK, self.prepare())
        state = load(self.fake.workspace)
        self.assertEqual((ROUTE_REPAIR, PREVIOUS, 2, 6),
                         (state.route, state.previous_coordinate, state.goal_covered_calls, state.goal_total_calls))
        # Split out of 1.5.0, the version now resolves to its own entry.
        self.assertEqual(("new-version", "1.5.0", "1.5.1"), (state.update_target["match_type"],
                                                             state.update_target["matched_metadata_version"],
                                                             state.update_target["resolved_metadata_version"]))

    def test_a_version_owning_its_metadata_is_measured_against_the_entry_before(self) -> None:
        # The nearest earlier supported version is 1.6.0, a tested version of the 1.5.0 entry.
        self.fake.supported_library(tested_versions=("1.5.0", "1.6.0"))
        self.fake.supported_version("2.0.0", covered=5, total=5)
        self.fake.issue("fails-native-image-run")
        self.assertEqual(OK, self.prepare())
        state = load(self.fake.workspace)
        self.assertEqual((ROUTE_REPAIR, PREVIOUS, 2, 6),
                         (state.route, state.previous_coordinate, state.goal_covered_calls, state.goal_total_calls))

    def test_fails_labels_share_the_repair_route(self) -> None:
        self.fake.supported_library()
        self.fake.issue("fails-native-image-build")
        self.assertEqual(OK, self.prepare())
        self.assertEqual(ROUTE_REPAIR, load(self.fake.workspace).route)

    def test_each_fails_label_repairs_under_its_own_fix_label(self) -> None:
        # The automation files each stage beside `library-unsupported-version` (§FS-forge-scope.1).
        for label, fix in (("fails-javac-compile", "fixes-javac-fail"), ("fails-java-run", "fixes-java-run-fail"),
                           ("fails-native-image-build", "fixes-native-image-build-fail"),
                           ("fails-native-image-run", "fixes-native-image-run-fail")):
            with self.subTest(label=label):
                parsed: issue.Issue = issue.parse_issue({
                    "state": "OPEN", "number": 7, "title": f"Fails {COORDINATE}",
                    "labels": [{"name": "library-unsupported-version"}, {"name": label}]})
                self.assertEqual((label, ROUTE_REPAIR, fix), (parsed.label, parsed.route, issue.TASK_TYPES[label]))

    def test_two_workflow_labels_are_a_bad_input(self) -> None:
        self.fake.issue("fails-javac-compile", extra_labels=("fails-java-run",))
        self.assertEqual(BAD_INPUT, self.prepare())

    def test_title_without_a_coordinate_is_a_bad_input(self) -> None:
        self.fake.issue("library-new-request", title="Support something")
        self.assertEqual(BAD_INPUT, self.prepare())

    def test_chunked_issue_without_exhaust_report_is_a_bad_input(self) -> None:
        self.fake.supported_library()
        self.fake.issue("library-update-request", extra_labels=("chunked-dynamic-access",))
        self.assertEqual(BAD_INPUT, self.prepare())
        # The close-out comment names the reason (§FS-forge-run-requirements.2).
        outcome: dict[str, str] = self.outcome()
        self.assertEqual(("bad-input", f"prepare: bad input: chunked issue has no exhaust report for {COORDINATE}", ""),
                         (outcome["kind"], outcome["reason"], outcome["detail"]))

    def outcome(self) -> dict[str, str]:
        return read_json(runtime_path(self.fake.workspace, forge_run.OUTCOME))

    def test_an_issue_gh_cannot_read_is_an_infrastructure_failure(self) -> None:
        # GitHub failed, not the issue: no hold (§FS-human-intervention-policy).
        self.fake.control({"issueError": "API rate limit exceeded for user ID 1.\nsecond line"})
        self.assertEqual(INFRASTRUCTURE, self.prepare())
        self.assertEqual(("prepare: infrastructure failure", "infrastructure"),
                         (self.outcome()["reason"], self.outcome()["kind"]))

    def test_a_library_without_a_jvm_variant_is_a_bad_input(self) -> None:
        # Every retry would fail the same way (§AR-forge-workflow-pipeline.4.1).
        self.fake.issue("library-new-request")
        self.fake.control({"scaffoldFails": "variant"})
        self.assertEqual(BAD_INPUT, self.prepare())
        outcome: dict[str, str] = self.outcome()
        self.assertEqual(("bad-input", f"prepare: bad input: {COORDINATE} has no artifact a JVM test project can use"),
                         (outcome["kind"], outcome["reason"]))
        # The lines naming the coordinate and why it does not resolve, not Gradle's hint after them.
        self.assertEqual("> Could not resolve all files for configuration ':detachedConfiguration1'.\n"
                         f"   > Could not resolve {COORDINATE}.\n"
                         f"      > No matching variant of {COORDINATE} was found. The consumer was configured to "
                         "find attribute 'org.gradle.jvm.environment' with value 'standard-jvm' but:",
                         outcome["detail"])

    def test_a_coordinate_without_a_jar_is_a_bad_input(self) -> None:
        # A BOM fails the same way on every retry (§AR-forge-workflow-pipeline.4.1).
        self.fake.issue("library-new-request")
        self.fake.control({"scaffoldFails": "bom"})
        self.assertEqual(BAD_INPUT, self.prepare())
        self.assertEqual((f"prepare: bad input: {COORDINATE} has no artifact a JVM test project can use",
                          f"> Failed to resolve JAR for {COORDINATE}"),
                         (self.outcome()["reason"], self.outcome()["detail"]))

    def test_a_coordinate_whose_jar_no_repository_serves_is_a_bad_input(self) -> None:
        # A POM without its JAR, as javax.ejb:ejb:2.0 on Maven Central, fails the same way on every
        # retry; the detail names the file (§AR-forge-workflow-pipeline.4.1).
        self.fake.issue("library-new-request")
        self.fake.control({"scaffoldFails": "jar"})
        self.assertEqual(BAD_INPUT, self.prepare())
        self.assertEqual((f"prepare: bad input: {COORDINATE} has no artifact a JVM test project can use",
                          "> Could not resolve all files for configuration ':detachedConfiguration1'.\n"
                          f"   > Could not find lib-2.0.0.jar ({COORDINATE})."),
                         (self.outcome()["reason"], self.outcome()["detail"]))

    def test_a_scaffold_that_fails_otherwise_is_an_infrastructure_failure(self) -> None:
        # An unreachable repository, or an artifact other than the coordinate missing, is retried
        # by a later cycle (§AR-forge-workflow-pipeline.4.1).
        self.fake.issue("library-new-request")
        for failure in ("unreachable", "elsewhere", "process"):
            with self.subTest(failure=failure):
                self.fake.control({"scaffoldFails": failure})
                self.assertEqual(INFRASTRUCTURE, self.prepare())
                self.assertEqual(("prepare: infrastructure failure", "infrastructure"),
                                 (self.outcome()["reason"], self.outcome()["kind"]))

    def assert_worktree_given_up(self) -> None:
        """The spawned worktree is gone with its Gradle home and daemons; its run branch stays.

        Without a state the close-out finds no worktree to give up (§AR-forge-workflow-pipeline.1).
        """
        run_name: str = "forge-" + COORDINATE.replace(":", "-")
        worktree: str = os.path.realpath(os.path.join(self.fake.tmp, "runs", "worktrees", run_name))
        home: str = worktree_gradle_home(checkout_gradle_home_for_repo(self.fake.repo), worktree)
        listed: str = subprocess.run(["git", "worktree", "list", "--porcelain"], cwd=self.fake.repo, check=True,
                                     stdout=subprocess.PIPE, text=True).stdout
        branches: list[str] = subprocess.run(["git", "branch", "--list", "--format=%(refname:short)", "rhei/*"],
                                             cwd=self.fake.repo, check=True, stdout=subprocess.PIPE,
                                             text=True).stdout.split()
        self.assertFalse(os.path.exists(runtime_path(self.fake.workspace, "state.json")))
        self.assertFalse(os.path.exists(worktree))
        self.assertNotIn(f"worktree {worktree}\n", listed)
        self.assertFalse(os.path.exists(home))
        self.assertEqual((home, worktree), self.fake.stop_daemons.call_args.args[:2])
        self.fake.stop_daemons.assert_called_once()
        self.assertEqual(1, len(branches), branches)

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

    def test_a_bad_input_reason_is_one_line(self) -> None:
        # Further lines follow as detail (§FS-forge-run-requirements.2).
        error: BadInput = BadInput("cannot parse the issue form.\nsecond line", "build output")
        self.assertEqual(("cannot parse the issue form", "second line\nbuild output"),
                         (error.reason, error.detail))

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

    def test_setup_agent_must_be_claude_code(self) -> None:
        self.fake.issue("library-new-request")
        self.assertEqual(BAD_INPUT, self.prepare(*prepare_arguments(setup_agent=WORKER_AGENT)))
        self.assertEqual(f"prepare: bad input: setup_agent {WORKER_AGENT} must be a claude-code target, "
                         "which has web search", self.outcome()["reason"])

    def test_codex_targets_are_rejected(self) -> None:
        self.fake.issue("library-new-request")
        self.assertEqual(BAD_INPUT, self.prepare(*prepare_arguments(worker_agent="codex:gpt-5.6-luna")))

    def test_human_reporter_request_is_kept_and_automation_bodies_are_not(self) -> None:
        self.fake.issue("library-new-request", body="Please register org.example.Plugin for reflection.")
        self.assertEqual(OK, self.prepare())
        self.assertTrue(load(self.fake.workspace).has_reporter_request)
        self.assertEqual("", prepare.reporter_request({"author": {"login": "app/github-actions"}, "body": "x"}))

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

    def test_validate_inputs_rejects_non_positive_budgets(self) -> None:
        namespace = argparse.Namespace(bulk_min_classes=0, bulk_passes=3, class_attempts=3, defer_classes=15,
                                       explore_passes=20, finalize_fixes=5, native_fixes=3,
                                       setup_agent=SETUP_AGENT,
                                       worker_agent=WORKER_AGENT)
        self.assertEqual("bulk_min_classes must be positive", prepare.validate_inputs(namespace))
        namespace.bulk_min_classes, namespace.finalize_fixes = 5, 0
        self.assertEqual("finalize_fixes must be positive", prepare.validate_inputs(namespace))
        namespace.finalize_fixes, namespace.native_fixes = 5, 0
        self.assertEqual("native_fixes must be positive", prepare.validate_inputs(namespace))


if __name__ == "__main__":
    unittest.main()
