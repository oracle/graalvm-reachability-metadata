# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Shared fixtures for the program tests: import paths, run states, and a fake repository.

The fake repository carries the scripted harness and GitHub of `fake_harness`,
so a test can drive the programs in the order `states.yaml` routes them
(§AR-forge-workflow-pipeline.3) and play the agent between them.
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
from typing import Any, Iterator

TESTS: str = os.path.dirname(os.path.abspath(__file__))
PROGRAMS: str = os.path.dirname(TESTS)
FORGE: str = os.path.abspath(os.path.join(PROGRAMS, "..", "..", ".."))
REPOSITORY: str = os.path.dirname(FORGE)
for path in (TESTS, PROGRAMS, FORGE):
    if path not in sys.path:
        sys.path.insert(0, path)

from fake_harness import FAKE_GH, FAKE_GRADLEW  # noqa: E402
from run_state import ROUTE_NEW, Budgets, RunState  # noqa: E402

COORDINATE: str = "org.example:lib:2.0.0"
PREVIOUS: str = "org.example:lib:1.5.0"
BUDGETS: Budgets = Budgets(bulk_min_classes=5, bulk_passes=3, class_attempts=3, defer_classes=15, explore_passes=20)


def run_state(route: str = ROUTE_NEW, worktree: str = "/tmp/worktree", **changes: Any) -> RunState:
    """A run state for unit tests that never touch the worktree."""
    state: RunState = RunState(
        issue_number=7, label="library-new-request", route=route, coordinate=COORDINATE,
        repo_checkout=REPOSITORY, worktree=worktree, branch="rhei/forge-lib-2.0.0", base_commit="base",
        checkpoint="base", started_at="2026-10-22T10:00:00+00:00",
        setup_agent="claude-code[medium]:sonnet", worker_agent="pi[high]:openai-codex/gpt-5.6-luna",
        budgets=Budgets(**vars(BUDGETS)),
    )
    for key, value in changes.items():
        setattr(state, key, value)
    return state


@contextlib.contextmanager
def working_directory(path: str) -> Iterator[None]:
    previous: str = os.getcwd()
    os.chdir(path)
    try:
        yield
    finally:
        os.chdir(previous)


def _executable(path: str, source: str) -> None:
    with open(path, "w", encoding="utf-8") as handle:
        handle.write(source)
    os.chmod(path, os.stat(path).st_mode | stat.S_IEXEC)


def _git(cwd: str, *args: str) -> None:
    subprocess.run(["git", *args], cwd=cwd, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


class FakeRepository:
    """A git repository with the scripted harness, an upstream remote, and a fake `gh` on PATH."""

    def __init__(self) -> None:
        self.tmp: str = tempfile.mkdtemp()
        self.repo: str = os.path.join(self.tmp, "repo")
        self.workspace: str = os.path.join(self.tmp, "runs", "ws")
        os.makedirs(self.workspace)
        os.makedirs(self.repo)
        _executable(os.path.join(self.repo, "gradlew"), FAKE_GRADLEW)
        with open(os.path.join(self.repo, ".gitignore"), "w", encoding="utf-8") as handle:
            handle.write("build/\ngradle-calls.log\nforge/local_repositories/\n")
        schema: str = os.path.join(".github", "scripts", "forge_pr_publisher", "schema.json")
        os.makedirs(os.path.dirname(os.path.join(self.repo, schema)))
        shutil.copyfile(os.path.join(REPOSITORY, schema), os.path.join(self.repo, schema))
        shutil.copytree(os.path.join(REPOSITORY, "stats", "schemas"), os.path.join(self.repo, "stats", "schemas"))
        shutil.copytree(os.path.join(FORGE, "prompt_templates", "dynamic_access"),
                        os.path.join(self.repo, "forge", "prompt_templates", "dynamic_access"))
        self.control_path: str = os.path.join(self.tmp, "control.json")
        self.control({"login": "octocat"})
        bin_dir: str = os.path.join(self.tmp, "bin")
        os.makedirs(bin_dir)
        _executable(os.path.join(bin_dir, "gh"), FAKE_GH)
        self.saved_environment: dict[str, str] = dict(os.environ)
        os.environ["PATH"] = bin_dir + os.pathsep + os.environ["PATH"]
        os.environ["FAKE_FORGE_CONTROL"] = self.control_path
        os.environ["FORGE_GRADLE_USER_HOME"] = os.path.join(self.tmp, "gradle-home")
        os.environ["RHEI_TASK_ID"] = "ws.forge"
        os.environ["RHEI_RESULT_PATH"] = os.path.join(self.workspace, "runtime", "results", "ws.forge.md")
        for command in (("init", "-q", "-b", "master"), ("config", "user.email", "t@example.com"),
                        ("config", "user.name", "Test"), ("config", "commit.gpgsign", "false")):
            _git(self.repo, *command)
        self.remote: str = os.path.join(self.tmp, "upstream.git")
        subprocess.run(["git", "init", "-q", "--bare", self.remote], check=True)
        _git(self.repo, "remote", "add", "origin", self.remote)
        self.commit("base")

    def close(self) -> None:
        os.environ.clear()
        os.environ.update(self.saved_environment)
        shutil.rmtree(self.tmp, ignore_errors=True)

    def control(self, values: dict[str, Any]) -> None:
        current: dict[str, Any] = {}
        if os.path.exists(self.control_path):
            with open(self.control_path, encoding="utf-8") as handle:
                current = json.load(handle)
        current.update(values)
        with open(self.control_path, "w", encoding="utf-8") as handle:
            json.dump(current, handle)

    def write(self, relative: str, text: str) -> str:
        path: str = os.path.join(self.repo, relative)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8") as handle:
            handle.write(text)
        return path

    def commit(self, message: str) -> None:
        _git(self.repo, "add", "-A")
        _git(self.repo, "commit", "-q", "--allow-empty", "-m", message)
        _git(self.repo, "push", "-q", "origin", "HEAD:master")

    def supported_library(self) -> None:
        """`org.example:lib:1.5.0` already supported, with tests, metadata, and stats."""
        self.write("metadata/org.example/lib/index.json", json.dumps([{
            "metadata-version": "1.5.0", "tested-versions": ["1.5.0"], "latest": True,
            "allowed-packages": ["org.example"], "source-code-url": "https://example.org/src",
            "test-code-url": "https://example.org/test", "documentation-url": "https://example.org/doc",
            "repository-url": "https://example.org/repo"}], indent=2))
        self.write("metadata/org.example/lib/1.5.0/reachability-metadata.json", "{}\n")
        self.write("tests/src/org.example/lib/1.5.0/build.gradle", "// lib 1.5.0\n")
        self.write("tests/src/org.example/lib/1.5.0/gradle.properties", "library.version = 1.5.0\n")
        self.write("tests/src/org.example/lib/1.5.0/user-code-filter.json", '{"rules": []}\n')
        self.write("tests/src/org.example/lib/1.5.0/src/test/java/org_example/CodecTest.java",
                   "package org_example;\npublic class CodecTest {}\n")
        self.write("stats/org.example/lib/1.5.0/stats.json", json.dumps({"versions": [{
            "version": "1.5.0", "dynamicAccess": {"coveredCalls": 2, "totalCalls": 6, "coverageRatio": 0.33,
                                                  "breakdown": {}}}]}))
        self.commit("support lib 1.5.0")

    def issue(self, label: str, title: str = f"Support {COORDINATE}", author: str = "reporter",
              body: str = "", extra_labels: tuple[str, ...] = ()) -> None:
        self.control({"issue": {"number": 7, "title": title, "state": "OPEN", "body": body,
                                "author": {"login": author},
                                "labels": [{"name": name} for name in (label, *extra_labels)]}})
