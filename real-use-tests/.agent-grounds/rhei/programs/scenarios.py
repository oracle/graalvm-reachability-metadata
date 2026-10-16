# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Scenario discovery.

A scenario is one test source file (§FS-real-use-test-contract.1.1) in the
project's language (§FS-real-use-test-contract.1.5), so the programs find
scenarios without parsing source beyond the package declaration.
"""
from __future__ import annotations

import os
import re

# Test source directory name -> (language, scenario file extension), as the scaffold lays them out.
SOURCE_LANGUAGES: dict[str, tuple[str, str]] = {
    "java": ("Java", ".java"),
    "kotlin": ("Kotlin", ".kt"),
    "scala": ("Scala", ".scala"),
    "groovy": ("Groovy", ".groovy"),
}
# The terminating semicolon is Java's; Kotlin, Scala, and Groovy omit it.
PACKAGE_DECLARATION: re.Pattern[str] = re.compile(r"^package\s+([\w.]+)\s*;?\s*$", re.MULTILINE)


def test_source_path(project_path: str) -> str:
    """The project's `src/test/<language>` directory: the one the scaffold created, else Java's.

    §FS-real-use-test-contract.1.5
    """
    for name in SOURCE_LANGUAGES:
        path: str = os.path.join(project_path, "src", "test", name)
        if os.path.isdir(path):
            return path
    return os.path.join(project_path, "src", "test", "java")


def language(test_source: str) -> str:
    """The language the scenarios under `test_source` are written in."""
    return SOURCE_LANGUAGES[os.path.basename(test_source)][0]


def _extension(test_source: str) -> str:
    return SOURCE_LANGUAGES[os.path.basename(test_source)][1]


def scenario_files(test_source: str) -> list[str]:
    """Every scenario source file under the test source directory, as sorted absolute paths."""
    extension: str = _extension(test_source)
    found: list[str] = []
    for directory, _, names in os.walk(test_source):
        found.extend(os.path.join(directory, name) for name in names if name.endswith(extension))
    return sorted(found)


def class_file(test_source: str, class_name: str) -> str:
    """The source file that declares top-level class `class_name`."""
    return os.path.join(test_source, *class_name.split(".")) + _extension(test_source)


def _read(path: str) -> str:
    with open(path, encoding="utf-8") as handle:
        return handle.read()


def test_packages(test_source: str) -> set[str]:
    """Packages the scenarios declare: the test's own namespace (§FS-real-use-tests.2.2)."""
    packages: set[str] = set()
    for path in scenario_files(test_source):
        match: re.Match[str] | None = PACKAGE_DECLARATION.search(_read(path))
        if match is not None:
            packages.add(match.group(1))
    return packages
