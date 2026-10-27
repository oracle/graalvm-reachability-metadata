# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""End-to-end tests for the real-use workflow programs.

A fake repository with a scripted `gradlew` stands in for the harness, and the
test plays the agent, so each program's exit code can be checked against the
routing of §AR-real-use-test-workflow.2 and the rules of
§FS-real-use-test-generation.
"""
from __future__ import annotations

import contextlib
import json
import os
import shutil
import stat
import subprocess
import sys
import tempfile
import unittest
from typing import Iterator

PROGRAMS: str = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, PROGRAMS)
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import build_tools  # noqa: E402
import publish  # noqa: E402
import real_use  # noqa: E402
from exit_codes import BAD_INPUT, INFRASTRUCTURE, NATIVE_FAILED, NO_SCENARIO_LEFT, OK, STYLE_FAILED  # noqa: E402
from fake_harness import FAKE_GH, FAKE_GRADLEW  # noqa: E402
from run_state import load  # noqa: E402

COORDINATE: str = "org.example:lib:1.0.0"
PROJECT: str = os.path.join("real-use-tests", "src", "org.example", "lib", "1.0.0")
STATS: str = os.path.join("real-use-tests", "stats", "org.example", "lib", "1.0.0", "stats.json")
STATS_SCHEMA: str = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "..", "..", "schemas",
                                 "stats-schema.json")
METADATA: str = os.path.join("src", "test", "resources", "META-INF", "native-image", "reachability-metadata.json")

ISSUE: dict = {"title": "Real-use tests for org.example:lib:1.0.0", "state": "OPEN",
               "labels": [{"name": "real-use-test"}]}

# The record Rhei writes after each agent invocation, as `runtime/accounting/invocations/<id>.json`.
INVOCATION: str = """{{"agent": "claude-code", "state": "{state}",
 "token_convention": "input-total-includes-cache",
 "tokens": {{"input": {{"total": {{"value": {total}}}, "cached_read": {{"value": {read}}},
                       "cache_write": {{"value": {write}}}}},
            "output": {{"total": {{"value": {output}}}}}}}}}
"""

SCENARIO: str = """/*
 * Copyright and related rights waived via CC0
 */
package com.example.realuse;

import org.example.lib.Client;

/**
 * {subsystem}. It drives the client end to end.
 */
public class {name} {{
    Client client;
}}
"""


@contextlib.contextmanager
def working_directory(path: str) -> Iterator[None]:
    previous: str = os.getcwd()
    os.chdir(path)
    try:
        yield
    finally:
        os.chdir(previous)


class WorkflowTest(unittest.TestCase):
    """One fake run, driven through the programs in the order states.yaml routes them."""

    def setUp(self) -> None:
        self.tmp: str = tempfile.mkdtemp()
        self.repo: str = os.path.join(self.tmp, "repo")
        self.workspace: str = os.path.join(self.tmp, "runs", "ws")
        os.makedirs(self.workspace)
        index_dir: str = os.path.join(self.repo, "metadata", "org.example", "lib")
        os.makedirs(index_dir)
        with open(os.path.join(index_dir, "index.json"), "w") as handle:
            json.dump([{"metadata-version": "1.0.0", "tested-versions": ["1.0.0"],
                        "allowed-packages": ["org.example.lib"]}], handle)
        gradlew: str = os.path.join(self.repo, "gradlew")
        with open(gradlew, "w") as handle:
            handle.write(FAKE_GRADLEW)
        os.chmod(gradlew, os.stat(gradlew).st_mode | stat.S_IEXEC)
        with open(os.path.join(self.repo, ".gitignore"), "w") as handle:
            handle.write("build/\n")
        for command in (["init", "-q", "-b", "master"], ["config", "user.email", "t@example.com"],
                        ["config", "user.name", "Test"], ["add", "-A"], ["commit", "-q", "-m", "base"]):
            subprocess.run(["git", *command], cwd=self.repo, check=True)
        # The upstream the publication branch is pushed to.
        self.remote: str = os.path.join(self.tmp, "upstream.git")
        subprocess.run(["git", "init", "-q", "--bare", self.remote], check=True)
        for command in (["remote", "add", "origin", self.remote], ["push", "-q", "origin", "master"]):
            subprocess.run(["git", *command], cwd=self.repo, check=True)
        self.control_path: str = os.path.join(self.tmp, "control.json")
        self.control({})
        os.environ["FAKE_GRADLE_CONTROL"] = self.control_path
        bin_dir: str = os.path.join(self.tmp, "bin")
        os.makedirs(bin_dir)
        fake_gh: str = os.path.join(bin_dir, "gh")
        with open(fake_gh, "w") as handle:
            handle.write(FAKE_GH)
        os.chmod(fake_gh, os.stat(fake_gh).st_mode | stat.S_IEXEC)
        self.saved_path: str = os.environ["PATH"]
        os.environ["PATH"] = bin_dir + os.pathsep + self.saved_path
        self.control({"issue": ISSUE})
        os.environ["RHEI_TASK_ID"] = "ws.real-use"
        os.environ["RHEI_RESULT_PATH"] = os.path.join(self.workspace, "runtime", "results", "ws.real-use.md")

    def tearDown(self) -> None:
        os.environ["PATH"] = self.saved_path
        shutil.rmtree(self.tmp, ignore_errors=True)

    def control(self, values: dict) -> None:
        current: dict = {}
        if os.path.exists(getattr(self, "control_path", "")):
            with open(self.control_path) as handle:
                current = json.load(handle)
        current.update(values)
        with open(self.control_path, "w") as handle:
            json.dump(current, handle)

    def step(self, *argv: str) -> int:
        with working_directory(self.workspace):
            return real_use.main(list(argv))

    def prepare(self, passes: str = "2") -> int:
        return self.step(
            "prepare", "--issue-number", "10900", "--scenario-passes", passes,
            "--repo-checkout", "../../repo", "--worktree-root", "../worktrees",
        )

    def project(self, *parts: str) -> str:
        return os.path.join(self.workspace, "..", "worktrees", "real-use-lib-1.0.0", PROJECT, *parts)

    def scenario_path(self, name: str) -> str:
        return self.project("src", "test", "java", "com", "example", "realuse", f"{name}.java")

    def write(self, path: str, text: str) -> None:
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w") as handle:
            handle.write(text)

    def read(self, path: str) -> str:
        with open(path) as handle:
            return handle.read()

    def assertValidStats(self, record: dict) -> None:
        """The record holds to the schema the trusted publisher validates it against."""
        from jsonschema import Draft202012Validator

        Draft202012Validator(json.loads(self.read(STATS_SCHEMA))).validate(record)

    def write_scenario(self, name: str, subsystem: str) -> None:
        self.write(self.scenario_path(name), SCENARIO.format(name=name, subsystem=subsystem))

    def runtime(self, *parts: str) -> str:
        return os.path.join(self.workspace, "runtime", "real-use", *parts)

    def invocation(self, name: str, state: str, total: int, read: int, write: int, output: int) -> None:
        """Play Rhei: record one agent invocation's token usage."""
        self.write(os.path.join(self.workspace, "runtime", "accounting", "invocations", f"{name}.json"),
                   INVOCATION.format(state=state, total=total, read=read, write=write, output=output))

    def preflight(self) -> None:
        """Play the preflight agent, then run its program."""
        self.write(self.project("build.gradle"), "// scaffold\n// supporting: com.example:pool:2.0\n")
        self.write(self.project("required-docker-images.txt"), "postgres:16\n")
        self.assertEqual(OK, self.step("apply-preflight"))

    def test_full_run_collects_metadata_fixes_style_and_commits(self) -> None:
        self.assertEqual(OK, self.prepare())
        state = load(self.workspace)
        self.assertEqual("rhei/real-use-lib-1.0.0", build_tools.git(state.worktree, "rev-parse", "--abbrev-ref", "HEAD").strip())
        self.assertEqual(f"path: {state.worktree}\n", self.read(os.path.join(self.workspace, "runtime", "worktree-refs", "ws.real-use.yaml")))

        # Preflight: a stray file and an early scenario are reverted; the declarations stay.
        stray: str = os.path.join(state.worktree, "STRAY.md")
        self.write(stray, "outside the project\n")
        self.write_scenario("EarlyTest", "Written too early")
        self.preflight()
        self.assertFalse(os.path.exists(stray))
        self.assertFalse(os.path.exists(self.scenario_path("EarlyTest")))
        self.assertTrue(os.path.exists(self.project("user-code-filter.json")))
        self.assertIn("supporting: com.example:pool:2.0", self.read(self.project("build.gradle")))
        declared: str = load(self.workspace).base_commit

        # The generate loop: two scenarios, one breaking checkstyle.
        self.write_scenario("ClientRoundTripTest", "Sends requests through the client")
        self.write_scenario("BrokenStyleTest", "Breaks the style")

        # A pass also wrote test-local metadata, which the collection drops.
        self.write(self.project(METADATA), json.dumps({"reflection": [
            {"type": "a.Junk", "condition": {"typeReached": "org.example.lib.Other"}}]}))
        # Native fails; the agent run collects the missing entry, plus two the rules reject.
        wanted: dict = {"type": "x.Y", "condition": {"typeReached": "org.example.lib.Client"}}
        self.control({"nativeNeeds": {"com.example.realuse.ClientRoundTripTest": "org.example.lib.Client"},
                      "collected": [wanted, {"type": "x.Unconditional"},
                                    {"type": "x.Z", "condition": {"typeReached": "com.other.Elsewhere"}}]})
        self.assertEqual(NATIVE_FAILED, self.step("native"))
        self.assertEqual(OK, self.step("collect-metadata"))
        self.assertEqual({"reflection": [wanted]}, json.loads(self.read(self.project(METADATA))))

        # The style checks fail and hand both outputs to the publication fix.
        self.control({"checkstyleFailing": ["com.example.realuse.BrokenStyleTest"], "spotlessFailing": "A.java"})
        self.assertEqual(STYLE_FAILED, self.step("style-check"))
        prompt: str = self.read(self.runtime("prompts", "publication-fix.md"))
        self.assertIn("failing `spotlessCheck`", prompt)
        self.assertIn("BrokenStyleTest.java:1: bad style", prompt)
        self.assertTrue(os.path.exists(self.scenario_path("BrokenStyleTest")))

        # The agent fixes the style; publish commits on top of the checkpoints, nothing folded.
        self.invocation("a", "generate", total=1010, read=900, write=100, output=40)
        self.invocation("b", "agent-publication-fix", total=820, read=600, write=200, output=30)
        self.control({"checkstyleFailing": [], "spotlessFailing": ""})
        self.write(os.path.join(state.worktree, "OUTSIDE.md"), "an agent edit outside the project\n")
        self.assertEqual(OK, self.step("publish"))
        self.assertEqual(declared, build_tools.git(state.worktree, "rev-parse", "HEAD~2").strip())
        self.assertIn("Add real-use tests for lib 1.0.0", build_tools.git(state.worktree, "log", "-1", "--format=%s", "HEAD~1"))
        committed: list[str] = build_tools.git(state.worktree, "show", "--name-only", "--format=", "HEAD~1").splitlines()
        self.assertTrue(all(path.startswith(PROJECT + os.sep) for path in committed))
        self.assertIn(os.path.join(PROJECT, METADATA), committed)
        self.assertIn(os.path.join(PROJECT, "src", "test", "java", "com", "example", "realuse", "BrokenStyleTest.java"), committed)
        # The stats record is the last commit, alone, and the branch carries it upstream.
        self.assertEqual([STATS], build_tools.git(state.worktree, "show", "--name-only", "--format=", "HEAD").splitlines())
        facts: dict = json.loads(self.read(os.path.join(state.worktree, STATS)))
        self.assertEqual({"line": {"covered": 15, "total": 20, "ratio": 0.75},
                          "instruction": {"covered": 70, "total": 100, "ratio": 0.7},
                          "method": {"covered": 8, "total": 10, "ratio": 0.8}}, facts["library_coverage"])
        self.assertEqual({"input": 30, "cached_input": 1500, "cache_write_input": 300, "output": 70},
                         facts["tokens"])
        self.assertEqual(2, facts["iterations"])
        self.assertEqual([], facts["removed"])
        self.assertEqual((10900, state.started_at), (facts["issue_number"], facts["timestamp"]))
        branch: str = f"ai/octocat/real-use-lib-1.0.0-{publish.publication_id(state)}"
        self.assertRegex(branch, r"-forge-10900-\d{14,20}-[0-9a-f]{12}$")
        self.assertEqual(build_tools.git(state.worktree, "rev-parse", "HEAD").strip(),
                         build_tools.git(self.remote, "rev-parse", branch).strip())
        self.assertEqual(f"Pushed {branch} for {COORDINATE}.\n", self.read(self.runtime("publication", "branch.md")))
        self.assertValidStats(facts)
        self.assertIn("publish exited 0", self.read(os.environ["RHEI_RESULT_PATH"]))

    def native_agent_fix_turn(self) -> None:
        """Route a native failure the collected entries cannot fix to the native fix agent."""
        self.assertEqual(OK, self.prepare(passes="1"))
        self.preflight()
        self.write_scenario("ClientRoundTripTest", "Sends requests through the client")
        self.write_scenario("BytecodeTest", "Generates proxies at run time")
        self.control({"nativeNeeds": {"com.example.realuse.ClientRoundTripTest": "org.example.lib.Client"},
                      "nativeUnfixable": ["com.example.realuse.BytecodeTest"]})
        self.assertEqual(NATIVE_FAILED, self.step("native"))
        self.assertEqual(NATIVE_FAILED, self.step("collect-metadata"))
        prompt: str = self.read(self.runtime("prompts", "native-fix.md"))
        self.assertIn("Last lines of the failing native run", prompt)
        self.assertIn(f"`{os.path.join(load(self.workspace).project_path, 'build.gradle')}`", prompt)

    def test_native_agent_fix_keeps_test_local_metadata_and_records_deletions(self) -> None:
        self.native_agent_fix_turn()
        # The agent: a test-local entry, an option, the bytecode scenario deleted, and a stray metadata/ edit.
        self.write(self.project(METADATA), json.dumps({"reflection": [
            {"type": "java.util.ArrayList", "condition": {"typeReached": "org.example.lib.Client"}}]}))
        self.write(self.project("build.gradle"), self.read(self.project("build.gradle"))
                   + "graalvmNative { binaries { test { buildArgs.add('--enable-url-protocols=http') } } }\n")
        os.remove(self.scenario_path("BytecodeTest"))
        stray: str = os.path.join("metadata", "org.example", "lib", "1.0.0", "x.json")
        self.write(os.path.join(load(self.workspace).worktree, stray), "{}")
        self.write(self.runtime("reports", "native-agent-fix.json"), json.dumps({
            "entriesAdded": 1,
            "options": [{"option": "--enable-url-protocols=http", "phase": "build", "fixes": "MalformedURLException"}],
            "deleted": [{"class": "com.example.realuse.BytecodeTest", "reason": "generates bytecode at run time"}]}))
        self.assertEqual(OK, self.step("style-check"))
        self.assertIn("no publication fix runs", self.read(self.runtime("prompts", "publication-fix.md")))
        self.assertEqual(OK, self.step("publish"))
        committed: list[str] = build_tools.git(load(self.workspace).worktree, "show", "--name-only", "--format=", "HEAD~1").splitlines()
        self.assertNotIn(stray, committed)
        self.assertIn(os.path.join(PROJECT, METADATA), committed)
        self.assertIn(os.path.join(PROJECT, "build.gradle"), committed)
        facts: dict = json.loads(self.read(os.path.join(load(self.workspace).worktree, STATS)))
        self.assertEqual([{"scenario": os.path.join(PROJECT, "src", "test", "java", "com", "example", "realuse",
                                                    "BytecodeTest.java"),
                           "reason": "generates bytecode at run time"}], facts["removed"])
        self.assertEqual(1, facts["iterations"])

    def test_no_scenario_left_ends_before_publication(self) -> None:
        self.native_agent_fix_turn()
        os.remove(self.scenario_path("BytecodeTest"))
        os.remove(self.scenario_path("ClientRoundTripTest"))
        self.assertEqual(NO_SCENARIO_LEFT, self.step("style-check"))

    def test_failed_push_fails_the_run_and_a_retry_pushes_the_same_commit(self) -> None:
        self.assertEqual(OK, self.prepare(passes="1"))
        self.preflight()
        self.write_scenario("ClientRoundTripTest", "Sends requests through the client")
        self.control({"loginFails": True})
        self.assertEqual(INFRASTRUCTURE, self.step("publish"))
        head: str = build_tools.git(load(self.workspace).worktree, "rev-parse", "HEAD").strip()
        self.control({"loginFails": False})
        self.assertEqual(OK, self.step("publish"))
        self.assertEqual(head, build_tools.git(load(self.workspace).worktree, "rev-parse", "HEAD").strip())

    def test_failed_coverage_run_fails_the_run_after_the_commit(self) -> None:
        self.assertEqual(OK, self.prepare(passes="1"))
        self.preflight()
        self.write_scenario("ClientRoundTripTest", "Sends requests through the client")
        self.control({"jacocoFails": True})
        self.assertEqual(INFRASTRUCTURE, self.step("publish"))
        self.assertFalse(os.path.exists(self.runtime("publication", "branch.md")))
        self.control({"jacocoFails": False})
        self.assertEqual(OK, self.step("publish"))

    def test_unchanged_project_has_nothing_to_publish(self) -> None:
        self.assertEqual(OK, self.prepare(passes="1"))
        self.preflight()
        self.assertEqual(NO_SCENARIO_LEFT, self.step("publish"))

    def test_unresolvable_libraries_fail_the_run(self) -> None:
        self.assertEqual(OK, self.prepare(passes="1"))
        self.control({"unresolvable": True})
        self.assertEqual(INFRASTRUCTURE, self.step("apply-preflight"))

    def test_malformed_input_is_a_bad_input(self) -> None:
        self.assertEqual(BAD_INPUT, self.prepare(passes="0"))
        for issue in (
                dict(ISSUE, state="CLOSED"),
                dict(ISSUE, labels=[{"name": "library-new-request"}]),
                dict(ISSUE, title="Real-use tests for gson"),
                dict(ISSUE, title="Compare org.example:lib:1.0.0 and org.example:lib:2.0.0"),
                None,
        ):
            self.control({"issue": issue})
            self.assertEqual(BAD_INPUT, self.prepare(), issue)

    def test_prepare_reads_the_coordinate_from_the_issue(self) -> None:
        self.assertEqual(OK, self.prepare())
        state = load(self.workspace)
        self.assertEqual((COORDINATE, 10900), (state.coordinate, state.issue_number))
        note: str = self.read(self.runtime("run.md"))
        self.assertIn("issue #10900", note)
        self.assertIn(f"`{COORDINATE}`", note)
        self.assertIn(f"`{PROJECT}/`", note)
        self.assertIn(f"`{os.path.join(state.project_path, 'build.gradle')}`", note)
        self.assertIn(f"`{state.metadata_path}`", note)
        self.assertIn(f"for Java sources: `{state.test_source_path}/`", note)

    def test_prepare_keeps_the_language_the_scaffold_chose(self) -> None:
        self.control({"scaffoldLanguage": "kotlin"})
        self.assertEqual(OK, self.prepare())
        state = load(self.workspace)
        self.assertEqual(os.path.join("src", "test", "kotlin"), state.test_source_dir)
        self.assertIn(f"for Kotlin sources: `{state.test_source_path}/`", self.read(self.runtime("run.md")))
        # Git keeps no empty directory; the recorded language survives its loss.
        os.rmdir(state.test_source_path)
        self.assertEqual(os.path.join(state.project_path, "src", "test", "kotlin"), load(self.workspace).test_source_path)


if __name__ == "__main__":
    unittest.main()
