# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Cover trusted version-to-version test comparisons (§AR-pr-body)."""

import importlib.util
import os
import subprocess
import sys
import tempfile
import unittest
from contextlib import contextmanager
from typing import Any, Iterator
from unittest.mock import patch

REPOSITORY_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
PUBLISHER_PATH = os.path.join(
    REPOSITORY_ROOT, ".github", "scripts", "forge_pr_publisher", "publisher.py",
)


def _load_publisher() -> Any:
    """Load the trusted publisher the way Actions runs it: straight from the file."""
    spec = importlib.util.spec_from_file_location("forge_pr_publisher_test_diff", PUBLISHER_PATH)
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


publisher = _load_publisher()


def _git(repo_path: str, *args: str) -> str:
    result = subprocess.run(
        ["git", *args],
        cwd=repo_path,
        check=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
    )
    return result.stdout


def _write(repo_path: str, relative_path: str, content: str) -> None:
    path = os.path.join(repo_path, relative_path)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as output_file:
        output_file.write(content)


def _test_path(version: str, relative_path: str) -> str:
    return os.path.join("tests", "src", "com.example", "demo", version, relative_path)


def _library(version: str) -> dict[str, str]:
    return {
        "group": "com.example",
        "artifact": "demo",
        "version": version,
        "coordinates": f"com.example:demo:{version}",
    }


def _validated_publication(repo_path: str) -> Any:
    descriptor: dict[str, Any] = {
        "previous_library": _library("1.0.0"),
        "library": _library("2.0.0"),
    }
    head_sha = _git(repo_path, "rev-parse", "HEAD").strip()
    return publisher.ValidatedPublication(descriptor, "descriptor.json", head_sha)


@contextmanager
def _working_directory(path: str) -> Iterator[None]:
    previous_path = os.getcwd()
    os.chdir(path)
    try:
        yield
    finally:
        os.chdir(previous_path)


class FixPublisherTestDiffTests(unittest.TestCase):

    def test_diff_compares_previous_and_current_version_trees(self) -> None:
        with tempfile.TemporaryDirectory() as repo_path:
            _git(repo_path, "init")
            _git(repo_path, "config", "user.email", "forge@example.com")
            _git(repo_path, "config", "user.name", "Forge Test")
            java_path = os.path.join("src", "test", "java", "DemoTest.java")
            _write(repo_path, _test_path("1.0.0", java_path), "class DemoTest { int value = 1; }\n")
            _write(repo_path, _test_path("2.0.0", java_path), "class DemoTest { int value = 2; }\n")
            _write(repo_path, _test_path("1.0.0", "user-code-filter.json"), "{\"old\": true}\n")
            _write(repo_path, _test_path("2.0.0", "user-code-filter.json"), "{\"new\": true}\n")
            _write(repo_path, _test_path("1.0.0", "gradle.properties"), "version=1.0.0\n")
            _write(repo_path, _test_path("2.0.0", "gradle.properties"), "version=2.0.0\n")
            _git(repo_path, "add", ".")
            _git(repo_path, "commit", "-m", "Add versioned test suites")

            with _working_directory(repo_path):
                section: str = publisher._format_bounded_test_diff_section(
                    _validated_publication(repo_path),
                )

        self.assertIn(
            "--- a/tests/src/com.example/demo/1.0.0/src/test/java/DemoTest.java",
            section,
        )
        self.assertIn(
            "+++ b/tests/src/com.example/demo/2.0.0/src/test/java/DemoTest.java",
            section,
        )
        self.assertNotIn("/dev/null", section)
        self.assertIn("2 files changed", section)
        self.assertNotIn("gradle.properties", section)

    def test_missing_previous_suite_is_reported(self) -> None:
        with tempfile.TemporaryDirectory() as repo_path:
            _git(repo_path, "init")
            _git(repo_path, "config", "user.email", "forge@example.com")
            _git(repo_path, "config", "user.name", "Forge Test")
            _write(
                repo_path,
                _test_path("2.0.0", os.path.join("src", "test", "java", "DemoTest.java")),
                "class DemoTest {}\n",
            )
            _git(repo_path, "add", ".")
            _git(repo_path, "commit", "-m", "Add current test suite")

            with _working_directory(repo_path):
                section: str = publisher._format_bounded_test_diff_section(
                    _validated_publication(repo_path),
                )

        self.assertIn("No previous test suite exists for `com.example:demo:1.0.0`", section)
        self.assertNotIn("/dev/null", section)

    def test_missing_current_suite_omits_comparison(self) -> None:
        with tempfile.TemporaryDirectory() as repo_path:
            _git(repo_path, "init")
            _git(repo_path, "config", "user.email", "forge@example.com")
            _git(repo_path, "config", "user.name", "Forge Test")
            _write(
                repo_path,
                _test_path("1.0.0", os.path.join("src", "test", "java", "DemoTest.java")),
                "class DemoTest {}\n",
            )
            _git(repo_path, "add", ".")
            _git(repo_path, "commit", "-m", "Add previous test suite")

            with _working_directory(repo_path):
                section: str = publisher._format_bounded_test_diff_section(
                    _validated_publication(repo_path),
                )

        self.assertEqual("", section)

    def test_diff_excerpt_remains_bounded(self) -> None:
        validated = publisher.ValidatedPublication(
            {
                "previous_library": _library("1.0.0"),
                "library": _library("2.0.0"),
            },
            "descriptor.json",
            "a" * 40,
        )
        oversized_diff = "Q" * (publisher.MAX_TEST_DIFF_CHARS + 1)
        with (
            patch.object(publisher, "_git_object_exists", return_value=True),
            patch.object(
                publisher,
                "git",
                side_effect=["1 file changed, 1 insertion(+)", oversized_diff],
            ),
        ):
            section: str = publisher._format_bounded_test_diff_section(validated)

        self.assertIn("[diff truncated]", section)
        self.assertNotIn(oversized_diff, section)
        self.assertEqual(section.count("Q"), publisher.MAX_TEST_DIFF_CHARS)


if __name__ == "__main__":
    unittest.main()
