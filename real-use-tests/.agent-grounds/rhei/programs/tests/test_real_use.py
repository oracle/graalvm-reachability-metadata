# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""End-to-end and unit tests for the real-use workflow programs.

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

import build_tools  # noqa: E402
import local_metadata  # noqa: E402
import real_use  # noqa: E402
import run_summary  # noqa: E402
from exit_codes import BAD_INPUT, INFRASTRUCTURE, NATIVE_FAILED, NO_SCENARIO_LEFT, OK, STYLE_FAILED  # noqa: E402
from run_state import load  # noqa: E402

COORDINATE: str = "org.example:lib:1.0.0"
PROJECT: str = os.path.join("real-use-tests", "src", "org.example", "lib", "1.0.0")
METADATA: str = os.path.join("src", "test", "resources", "META-INF", "native-image", "reachability-metadata.json")

# The scripted harness. Control file keys: unresolvable (the filter task fails),
# spotlessFailing (a file spotless reports), checkstyleFailing (classes checkstyle
# rejects), nativeNeeds
# (class -> typeReached it needs), nativeUnfixable, collected (the entries the
# native-image agent writes when realUseJavaTest runs with an agent output),
# jacocoFails (the coverage run fails).
FAKE_GRADLEW: str = r'''#!/usr/bin/env python3
import glob, json, os, sys
task = next(arg for arg in sys.argv[1:] if not arg.startswith("-"))
control = json.load(open(os.environ["FAKE_GRADLE_CONTROL"]))
root = os.path.dirname(os.path.abspath(__file__))
project = os.path.join(root, "real-use-tests", "src", "org.example", "lib", "1.0.0")
java = os.path.join(project, "src", "test", "java")
def present():
    names = []
    for path in glob.glob(os.path.join(java, "**", "*.java"), recursive=True):
        names.append(os.path.relpath(path, java)[:-5].replace(os.sep, "."))
    return names
def report(lane, failing):
    out = os.path.join(project, "build", "test-results", lane)
    os.makedirs(out, exist_ok=True)
    cases = "".join(
        '<testcase classname="%s" name="t">%s</testcase>' % (name, "<failure/>" if name in failing else "")
        for name in present())
    open(os.path.join(out, "TEST-all.xml"), "w").write("<testsuite>%s</testsuite>" % cases)
    return 1 if failing else 0
agent_output = next((arg.split("=", 1)[1] for arg in sys.argv[1:]
                     if arg.startswith("-Ptck.realUse.agentOutput=")), None)
def native_failing():
    local = os.path.join(project, "src", "test", "resources", "META-INF", "native-image", "reachability-metadata.json")
    reached = set()
    if os.path.exists(local):
        for entry in json.load(open(local)).get("reflection", []):
            reached.add(entry["condition"]["typeReached"])
    needs = control.get("nativeNeeds", {})
    return [name for name in present()
            if name in control.get("nativeUnfixable", []) or (name in needs and needs[name] not in reached)]
if task == "scaffoldRealUseTest":
    os.makedirs(java, exist_ok=True)
    open(os.path.join(project, "build.gradle"), "w").write("// scaffold\n")
elif task == "generateRealUseFilter":
    if control.get("unresolvable"):
        sys.exit(1)
    rules = [{"excludeClasses": "**"}, {"includeClasses": "org.example.lib.**"}]
    open(os.path.join(project, "user-code-filter.json"), "w").write(json.dumps({"rules": rules}))
elif task == "realUseJavaTest":
    if agent_output:
        out = open(os.path.join(agent_output, "reachability-metadata.json"), "w")
        out.write(json.dumps({"reflection": control.get("collected", [])}))
elif task == "spotlessCheck":
    if control.get("spotlessFailing"):
        print("The following files had format violations: %s" % control["spotlessFailing"])
        sys.exit(1)
elif task == "checkstyle":
    failing = [n for n in present() if n in control.get("checkstyleFailing", [])]
    for name in failing:
        print("[ERROR] %s.java:1: bad style" % os.path.join(java, *name.split(".")))
    sys.exit(1 if failing else 0)
elif task == "realUseNativeTest":
    sys.exit(report("test-native", native_failing()))
elif task == "realUseJacocoReport":
    if control.get("jacocoFails"):
        sys.exit(1)
    out = os.path.join(project, "build", "reports", "jacoco", "test")
    os.makedirs(out, exist_ok=True)
    open(os.path.join(out, "jacocoTestReport.xml"), "w").write(
        '<report name="lib"><package name="org/example/lib">'
        '<counter type="LINE" missed="1" covered="1"/></package>'
        '<counter type="INSTRUCTION" missed="30" covered="70"/>'
        '<counter type="LINE" missed="5" covered="15"/>'
        '<counter type="METHOD" missed="2" covered="8"/></report>')
'''

# The fake Forge real-use route: records its arguments and prints the pushed branch,
# or fails when the control file says so.
FAKE_PUBLISH_ROUTE: str = r'''import json, os, sys
control = json.load(open(os.environ["FAKE_GRADLE_CONTROL"]))
if control.get("pushFails"):
    print("gh: not authenticated")
    sys.exit(1)
args = sys.argv[1:]
json.dump(args, open(os.environ["FAKE_PUBLISH_RECORD"], "w"))
print("Pushed ai/octocat/real-use-lib-1.0.0-forge-real-use-1 for org.example:lib:1.0.0.")
'''

# The fake `gh`: serves the control file's `issue` for `gh issue view`.
FAKE_GH: str = r'''#!/usr/bin/env python3
import json, os, sys
issue = json.load(open(os.environ["FAKE_GRADLE_CONTROL"])).get("issue")
if sys.argv[1:3] != ["issue", "view"] or issue is None:
    print("issue not found", file=sys.stderr)
    sys.exit(1)
print(json.dumps(issue))
'''
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
        route: str = os.path.join(self.repo, "forge", "git_scripts", "publish_real_use_tests.py")
        os.makedirs(os.path.dirname(route))
        with open(route, "w") as handle:
            handle.write(FAKE_PUBLISH_ROUTE)
        with open(os.path.join(self.repo, ".gitignore"), "w") as handle:
            handle.write("build/\n")
        for command in (["init", "-q", "-b", "master"], ["config", "user.email", "t@example.com"],
                        ["config", "user.name", "Test"], ["add", "-A"], ["commit", "-q", "-m", "base"]):
            subprocess.run(["git", *command], cwd=self.repo, check=True)
        self.control_path: str = os.path.join(self.tmp, "control.json")
        self.control({})
        os.environ["FAKE_GRADLE_CONTROL"] = self.control_path
        os.environ["FAKE_PUBLISH_RECORD"] = os.path.join(self.tmp, "publish.json")
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
        self.assertEqual(declared, build_tools.git(state.worktree, "rev-parse", "HEAD~1").strip())
        self.assertIn("Add real-use tests for lib 1.0.0", build_tools.git(state.worktree, "log", "-1", "--format=%s"))
        committed: list[str] = build_tools.git(state.worktree, "show", "--name-only", "--format=", "HEAD").splitlines()
        self.assertTrue(all(path.startswith(PROJECT + os.sep) for path in committed))
        self.assertIn(os.path.join(PROJECT, METADATA), committed)
        self.assertIn(os.path.join(PROJECT, "src", "test", "java", "com", "example", "realuse", "BrokenStyleTest.java"), committed)
        facts: dict = json.loads(self.read(self.runtime("publication", "render.json")))
        self.assertEqual({"line": {"covered": 15, "total": 20, "ratio": 0.75},
                          "instruction": {"covered": 70, "total": 100, "ratio": 0.7},
                          "method": {"covered": 8, "total": 10, "ratio": 0.8}}, facts["library_coverage"])
        self.assertEqual({"input": 30, "cached_input": 1500, "cache_write_input": 300, "output": 70},
                         facts["tokens"])
        self.assertEqual(2, facts["iterations"])
        self.assertEqual([], facts["removed"])
        pushed: list[str] = json.loads(self.read(os.environ["FAKE_PUBLISH_RECORD"]))
        self.assertEqual(["--repo-path", state.worktree, "--coordinates", COORDINATE, "--issue-number", "10900",
                          "--timestamp", state.started_at, "--render-json", self.runtime("publication", "render.json")],
                         pushed[:10])
        self.assertIn("Pushed ai/octocat/", self.read(self.runtime("publication", "branch.md")))
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
        committed: list[str] = build_tools.git(load(self.workspace).worktree, "show", "--name-only", "--format=", "HEAD").splitlines()
        self.assertNotIn(stray, committed)
        self.assertIn(os.path.join(PROJECT, METADATA), committed)
        self.assertIn(os.path.join(PROJECT, "build.gradle"), committed)
        facts: dict = json.loads(self.read(self.runtime("publication", "render.json")))
        self.assertEqual([{"scenario": os.path.join(PROJECT, "src", "test", "java", "com", "example", "realuse",
                                                    "BytecodeTest.java"),
                           "reason": "generates bytecode at run time"}], facts["removed"])
        self.assertEqual(0, facts["iterations"])

    def test_no_scenario_left_ends_before_publication(self) -> None:
        self.native_agent_fix_turn()
        os.remove(self.scenario_path("BytecodeTest"))
        os.remove(self.scenario_path("ClientRoundTripTest"))
        self.assertEqual(NO_SCENARIO_LEFT, self.step("style-check"))

    def test_failed_push_fails_the_run_and_a_retry_pushes_the_same_commit(self) -> None:
        self.assertEqual(OK, self.prepare(passes="1"))
        self.preflight()
        self.write_scenario("ClientRoundTripTest", "Sends requests through the client")
        self.control({"pushFails": True})
        self.assertEqual(INFRASTRUCTURE, self.step("publish"))
        head: str = build_tools.git(load(self.workspace).worktree, "rev-parse", "HEAD").strip()
        self.control({"pushFails": False})
        self.assertEqual(OK, self.step("publish"))
        self.assertEqual(head, build_tools.git(load(self.workspace).worktree, "rev-parse", "HEAD").strip())

    def test_failed_coverage_run_fails_the_run_after_the_commit(self) -> None:
        self.assertEqual(OK, self.prepare(passes="1"))
        self.preflight()
        self.write_scenario("ClientRoundTripTest", "Sends requests through the client")
        self.control({"jacocoFails": True})
        self.assertEqual(INFRASTRUCTURE, self.step("publish"))
        self.assertFalse(os.path.exists(os.environ["FAKE_PUBLISH_RECORD"]))
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


class UnitTest(unittest.TestCase):
    """The parsing the programs rely on, checked in isolation."""

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
                # Killed before it reported usage: an iteration, but no tokens.
                "c": {"tokens": {"input": {"total": {"status": "missing"}}}},
            }
            for name, record in records.items():
                with open(os.path.join(invocations, f"{name}.json"), "w") as handle:
                    json.dump(record, handle)
            tokens, iterations = run_summary.agent_cost(tmp)
        self.assertEqual({"input": 17, "cached_input": 150, "cache_write_input": None, "output": 8}, tokens)
        self.assertEqual(3, iterations)

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
