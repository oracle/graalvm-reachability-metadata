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
from unittest import mock

TESTS: str = os.path.dirname(os.path.abspath(__file__))
PROGRAMS: str = os.path.dirname(TESTS)
FORGE: str = os.path.abspath(os.path.join(PROGRAMS, "..", "..", ".."))
REPOSITORY: str = os.path.dirname(FORGE)
for path in (TESTS, PROGRAMS, FORGE):
    if path not in sys.path:
        sys.path.insert(0, path)

import fix_route  # noqa: E402
import prepare  # noqa: E402
import index_record  # noqa: E402
from utility_scripts.native_image_artifact import NativeImageEligibility  # noqa: E402
from fake_harness import FAKE_GH, FAKE_GRADLEW  # noqa: E402
from run_state import ROUTE_NEW, Budgets, RunState, runtime_path, write_json  # noqa: E402

COORDINATE: str = "org.example:lib:2.0.0"
PREVIOUS: str = "org.example:lib:1.5.0"
BUDGETS: Budgets = Budgets(bulk_min_classes=5, bulk_passes=3, class_attempts=3, defer_classes=15, explore_passes=20,
                           finalize_fixes=5, native_fixes=3)


def run_state(route: str = ROUTE_NEW, worktree: str = "/tmp/worktree", **changes: Any) -> RunState:
    """A run state for unit tests that never touch the worktree."""
    state: RunState = RunState(
        issue_number=7, label="library-new-request", route=route, coordinate=COORDINATE,
        repo_checkout=REPOSITORY, worktree=worktree, branch=prepare.run_branch(worktree), base_commit="base",
        checkpoint="base", started_at="2026-10-22T10:00:00+00:00",
        setup_agent="claude-code[medium]:sonnet", worker_agent="pi[high]:openai-codex/gpt-5.6-luna",
        budgets=Budgets(**vars(BUDGETS)),
    )
    for key, value in changes.items():
        setattr(state, key, value)
    return state


def play_fix(workspace: str, result: str = "fixed", summary: str = "played", changed: tuple[str, ...] = ()) -> None:
    """Play `fix`: write the open request's report at the one path the `fix` state declares."""
    write_json(runtime_path(workspace, fix_route.OPEN_REPORT),
               {"result": result, "changed": list(changed), "summary": summary})


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
        # No image is ever pulled, so the matrix's no-new-image check sees an empty list.
        _executable(os.path.join(bin_dir, "docker"), "#!/bin/sh\nexit 0\n")
        script: str = os.path.join(".github", "workflows", "scripts", "run-consecutive-tests.sh")
        os.makedirs(os.path.dirname(os.path.join(self.repo, script)))
        shutil.copyfile(os.path.join(REPOSITORY, script), os.path.join(self.repo, script))
        shutil.copyfile(os.path.join(REPOSITORY, "ci.json"), os.path.join(self.repo, "ci.json"))
        # The fake harness has no root build to warm; `prepare` asks for the shared cache all the same.
        self.shared_cache_patch: Any = mock.patch.object(prepare, "ensure_shared_dependency_cache")
        self.shared_cache: mock.MagicMock = self.shared_cache_patch.start()
        self.artifact_patch: Any = mock.patch.object(index_record, "inspect_maven_artifact",
                                                     return_value=NativeImageEligibility(False))
        self.artifact: mock.MagicMock = self.artifact_patch.start()
        self.saved_environment: dict[str, str] = dict(os.environ)
        os.environ["PATH"] = bin_dir + os.pathsep + os.environ["PATH"]
        os.environ["FAKE_FORGE_CONTROL"] = self.control_path
        os.environ["FORGE_GRADLE_USER_HOME"] = os.path.join(self.tmp, "gradle-home")
        # The fake index names unreachable URLs; the setup tail must not pause between download attempts.
        os.environ["FORGE_SOURCE_RETRY_SECONDS"] = "0"
        os.environ["RHEI_TASK_ID"] = "ws.forge"
        os.environ["RHEI_RESULT_PATH"] = os.path.join(self.workspace, "runtime", "results", "ws.forge.md")
        os.environ["GRAALVM_HOME_25_0"] = self.graalvm("graalvm-25")
        os.environ["GRAALVM_HOME_LATEST_EA"] = self.graalvm("graalvm-ea")
        for command in (("init", "-q", "-b", "master"), ("config", "user.email", "t@example.com"),
                        ("config", "user.name", "Test"), ("config", "commit.gpgsign", "false")):
            _git(self.repo, *command)
        self.remote: str = os.path.join(self.tmp, "upstream.git")
        subprocess.run(["git", "init", "-q", "--bare", self.remote], check=True)
        _git(self.repo, "remote", "add", "origin", self.remote)
        self.commit("base")

    def graalvm(self, name: str) -> str:
        """A GraalVM home whose `java` and `native-image` succeed, with the metadata schema."""
        home: str = os.path.join(self.tmp, name)
        os.makedirs(os.path.join(home, "bin"))
        for executable in ("java", "native-image"):
            _executable(os.path.join(home, "bin", executable), "#!/bin/sh\nexit 0\n")
        schema: str = os.path.join(home, "lib", "svm", "schemas", "reachability-metadata-schema.json")
        os.makedirs(os.path.dirname(schema))
        with open(schema, "w", encoding="utf-8") as handle:
            handle.write("{}\n")
        return home

    def close(self) -> None:
        self.shared_cache_patch.stop()
        self.artifact_patch.stop()
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

    def supported_library(self, tested_versions: tuple[str, ...] = ("1.5.0",)) -> None:
        """`org.example:lib:1.5.0` already supported, with tests, metadata, and stats."""
        self.write("metadata/org.example/lib/index.json", json.dumps([self.index_entry("1.5.0", tested_versions)], indent=2))
        self.supported_files("1.5.0", covered=2, total=6)
        self.commit("support lib 1.5.0")

    def supported_version(self, version: str, covered: int, total: int) -> None:
        """A further supported version with its own metadata, tests, and stats, now the latest."""
        with open(os.path.join(self.repo, "metadata/org.example/lib/index.json"), encoding="utf-8") as handle:
            entries: list[dict[str, Any]] = json.load(handle)
        for entry in entries:
            entry["latest"] = False
        entries.append(self.index_entry(version, (version,)))
        self.write("metadata/org.example/lib/index.json", json.dumps(entries, indent=2))
        self.supported_files(version, covered, total)
        self.commit(f"support lib {version}")

    @staticmethod
    def index_entry(version: str, tested_versions: tuple[str, ...]) -> dict[str, Any]:
        return {"metadata-version": version, "tested-versions": list(tested_versions), "latest": True,
                "allowed-packages": ["org.example"], "source-code-url": "https://example.org/src",
                "test-code-url": "https://example.org/test", "documentation-url": "https://example.org/doc",
                "repository-url": "https://example.org/repo"}

    def supported_files(self, version: str, covered: int, total: int) -> None:
        self.write(f"metadata/org.example/lib/{version}/reachability-metadata.json", "{}\n")
        self.write(f"tests/src/org.example/lib/{version}/build.gradle", f"// lib {version}\n")
        self.write(f"tests/src/org.example/lib/{version}/gradle.properties", f"library.version = {version}\n")
        self.write(f"tests/src/org.example/lib/{version}/user-code-filter.json", '{"rules": []}\n')
        self.write(f"tests/src/org.example/lib/{version}/src/test/java/org_example/CodecTest.java",
                   "package org_example;\npublic class CodecTest {}\n")
        self.write(f"stats/org.example/lib/{version}/stats.json", json.dumps({"versions": [{
            "version": version, "dynamicAccess": {"coveredCalls": covered, "totalCalls": total,
                                                  "coverageRatio": round(covered / max(total, 1), 2), "breakdown": {}}}]}))

    def issue(self, label: str, title: str = f"Support {COORDINATE}", author: str = "reporter",
              body: str = "", extra_labels: tuple[str, ...] = ()) -> None:
        self.control({"issue": {"number": 7, "title": title, "state": "OPEN", "body": body,
                                "author": {"login": author},
                                "labels": [{"name": name} for name in (label, *extra_labels)]}})
